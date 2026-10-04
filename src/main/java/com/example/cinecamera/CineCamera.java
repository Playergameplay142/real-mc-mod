package com.example.cinecamera;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CineCamera {
	private CineCamera() {}

	// ---- ayarlar ----
	static final double SHOULDER = 0.6;        // omuz ustu yan kayma
	static final double BASE_DIST_3P = 3.4;
	static final double BASE_DIST_2P = 3.0;
	static final double ROT_STIFFNESS = 16.0;  // 3. sahis yaylanma (yuksek = sert)
	static final double GOLDEN_YAW = 5.0;      // 2. sahis: kisi kadrajin yanina (derece)

	// ---- durum ----
	private static boolean broken, init;
	private static int lastType = -1;
	private static long lastNano;
	private static double sYaw, sPitch, sDist, sUp, sOut;
	private static Vec3 sPos = Vec3.ZERO;
	private static double cX, cY, cZ; private static float cYaw, cPitch; private static boolean cValid;

	public static void apply(Camera cam) {
		if (broken) return;
		try {
			if (!CameraAccess.available()) { broken = true; CineCameraClient.LOG.warn("Camera setPosition/setRotation bulunamadi, mod devre disi."); return; }
			Minecraft mc = Minecraft.getInstance();
			Entity e = mc.getCameraEntity();
			if (e == null || mc.level == null) return;
			Pose pose = e.getPose();
			if (pose != Pose.STANDING && pose != Pose.CROUCHING) { cValid = false; return; }

			int type = mc.options.getCameraType().ordinal(); // 0=1.sahis 1=3.sahis arka 2=onden
			long now = System.nanoTime();
			if (type != lastType) { init = false; lastType = type; cValid = false; }
			if (cValid && now - lastNano < 1_500_000L) { CameraAccess.set(cam, cX, cY, cZ, cYaw, cPitch); return; }
			double dt = Mth.clamp((now - lastNano) / 1.0e9, 0.0005, 0.1);
			lastNano = now;

			float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			switch (type) {
				case 0 -> firstPerson(cam, e, pt);
				case 1 -> thirdPerson(cam, e, pt, dt);
				default -> secondPerson(cam, mc, e, pt, dt);
			}
			init = true;
		} catch (Throwable t) {
			broken = true;
			CineCameraClient.LOG.error("CineCamera hata verdi, vanilla kameraya donuldu.", t);
		}
	}

	// ---------- 1. SAHIS: boyun ekseni ----------
	private static void firstPerson(Camera cam, Entity e, float pt) throws Exception {
		float yaw = e.getViewYRot(pt), pitch = e.getViewXRot(pt);
		Vec3 eye = e.getEyePosition(pt);
		double p = Math.max(0, pitch) / 90.0;           // asagi bakinca 0..1
		double yr = Math.toRadians(yaw);
		Vec3 pos = eye.add(-Math.sin(yr) * 0.14 * p, -0.06 * p, Math.cos(yr) * 0.14 * p);
		pos = clip(e, eye, pos);
		put(cam, pos, yaw, pitch);
	}

	// ---------- 3. SAHIS: omuz ustu + atalet + carpisma ----------
	private static void thirdPerson(Camera cam, Entity e, float pt, double dt) throws Exception {
		float yaw = e.getViewYRot(pt), pitch = e.getViewXRot(pt);
		Vec3 eye = e.getEyePosition(pt);
		Vec3 v = e.getDeltaMovement();
		double hs = Math.sqrt(v.x * v.x + v.z * v.z);
		double sprintF = Mth.clamp((hs - 0.22) / 0.10, 0, 1);
		double fallF = Mth.clamp((-v.y - 0.5) / 0.8, 0, 1);
		double tDist = BASE_DIST_3P + 0.8 * sprintF + 0.3 * fallF;
		double tUp = 0.1 + 0.5 * fallF;

		if (!init) { sYaw = yaw; sPitch = pitch; sDist = tDist; sUp = tUp; sOut = tDist; }
		double a = 1 - Math.exp(-ROT_STIFFNESS * dt);
		sYaw += Mth.wrapDegrees(yaw - sYaw) * a;
		sPitch += (pitch - sPitch) * a;
		double b = 1 - Math.exp(-5.0 * dt);
		sDist += (tDist - sDist) * b;
		sUp += (tUp - sUp) * b;

		double yr = Math.toRadians(sYaw), pr = Math.toRadians(sPitch);
		Vec3 right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
		Vec3 look = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));

		Vec3 shoulder = clip(e, eye, eye.add(right.scale(SHOULDER)).add(0, sUp, 0));

		double allowed = sDist;
		for (int i = 0; i < 8; i++) {
			Vec3 off = new Vec3((i & 1) * 0.2 - 0.1, (i >> 1 & 1) * 0.2 - 0.1, (i >> 2 & 1) * 0.2 - 0.1);
			Vec3 s = shoulder.add(off), t = s.subtract(look.scale(sDist));
			BlockHitResult h = e.level().clip(new ClipContext(s, t, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
			if (h.getType() != HitResult.Type.MISS) allowed = Math.min(allowed, h.getLocation().distanceTo(s));
		}
		allowed = Math.max(allowed - 0.05, 0.25);
		if (allowed < sOut) sOut = allowed; else sOut += (Math.min(sDist, allowed) - sOut) * (1 - Math.exp(-6.0 * dt));

		put(cam, shoulder.subtract(look.scale(sOut)), (float) sYaw, (float) sPitch);
	}

	// ---------- 2. SAHIS: onden, gimbal + zoom ----------
	private static void secondPerson(Camera cam, Minecraft mc, Entity e, float pt, double dt) throws Exception {
		float yaw = e.getViewYRot(pt), pitch = e.getViewXRot(pt);
		Vec3 focus = e.getEyePosition(pt).add(0, -0.25, 0);

		double zoom = 0;
		if (e instanceof LivingEntity le) {
			if (le.getHealth() <= le.getMaxHealth() * 0.35f) zoom += 1.0;
			if (le.hurtTime > 0) zoom += 0.4;
		}
		if (mc.screen != null) zoom += 1.2;      // ticaret / diyalog ekranlari
		double tDist = BASE_DIST_2P - zoom;

		if (!init) { sYaw = yaw; sPitch = pitch * 0.6; sDist = tDist; sPos = null; }
		double a = 1 - Math.exp(-6.0 * dt);
		sYaw += Mth.wrapDegrees(yaw - sYaw) * a;
		sPitch += (pitch * 0.6 - sPitch) * a;
		sDist += (tDist - sDist) * (1 - Math.exp(-4.0 * dt));

		double yr = Math.toRadians(sYaw), pr = Math.toRadians(sPitch);
		Vec3 dir = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)); // karakterin onu
		Vec3 right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
		Vec3 target = focus.add(dir.scale(sDist)).add(right.scale(-0.35));
		target = clip(e, focus, target);

		if (sPos == null) sPos = target;
		double k = 1 - Math.exp(-10.0 * dt);
		sPos = sPos.add(target.subtract(sPos).scale(k));
		Vec3 p = clip(e, focus, sPos);

		Vec3 d = focus.subtract(p);
		double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
		float cy = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)) + GOLDEN_YAW);
		float cp = (float) -Math.toDegrees(Math.atan2(d.y, horiz));
		put(cam, p, cy, cp);
	}

	// ---------- yardimcilar ----------
	private static Vec3 clip(Entity e, Vec3 from, Vec3 to) {
		BlockHitResult h = e.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
		if (h.getType() == HitResult.Type.MISS) return to;
		Vec3 dir = to.subtract(from);
		double len = dir.length();
		if (len < 1e-4) return from;
		double d = Math.max(h.getLocation().distanceTo(from) - 0.15, 0);
		return from.add(dir.scale(d / len));
	}

	private static void put(Camera cam, Vec3 p, float yaw, float pitch) throws Exception {
		cX = p.x; cY = p.y; cZ = p.z; cYaw = yaw; cPitch = pitch; cValid = true;
		CameraAccess.set(cam, cX, cY, cZ, yaw, pitch);
	}
}
