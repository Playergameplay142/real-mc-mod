package com.example.cinecamera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 1. sahista oyuncunun kendi govdesini cizerken: kafayi gizler, kollari "ilk bakis" icin kaldirir,
 * esya/arac/kalkan animasyonlarini uygular. Zirh, ayni model pozunu paylastigi icin otomatik uyar.
 * Tum pozlar mutlak degerle yazilir (idempotent).
 */
public final class FirstPersonBody {
	private FirstPersonBody() {}

	private static final Set<HumanoidModel<?>> HIDDEN = Collections.newSetFromMap(new IdentityHashMap<>());
	private static boolean broken;

	// ---- ayarlanabilir sabitler (radyan) ----
	static final float READY_ITEM_X = -0.80f, READY_ITEM_Y = -0.18f;
	static final float READY_FIST_X = -0.35f, READY_FIST_Y = -0.10f;
	static final float SHIELD_X = -1.40f, SHIELD_Y = -0.70f;

	static void fail(Throwable t) {
		if (!broken) { broken = true; CineCameraClient.LOG.error("FirstPersonBody hata verdi, devre disi.", t); }
	}

	public static void apply(HumanoidModel<?> m, HumanoidRenderState st) {
		if (broken) return;
		if (!isLocal(st)) {
			if (HIDDEN.remove(m)) m.head.visible = true;   // paylasilan modeli digerleri icin geri ac
			return;
		}
		HIDDEN.add(m);
		m.head.visible = false;      // sapka (hat) kafanin cocugu oldugu icin birlikte gizlenir
		poseArms(m, st);
	}

	/** Bu render state yerel oyuncuya mi ait? (konum eslesmesi) */
	private static boolean isLocal(HumanoidRenderState s) {
		if (!CineCamera.fullBodyActive()) return false;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) return false;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		double dx = s.x - Mth.lerp(pt, (float) p.xOld, (float) p.getX());
		double dy = s.y - Mth.lerp(pt, (float) p.yOld, (float) p.getY());
		double dz = s.z - Mth.lerp(pt, (float) p.zOld, (float) p.getZ());
		return dx * dx + dy * dy + dz * dz < 0.04;
	}

	private static void poseArms(HumanoidModel<?> m, HumanoidRenderState st) {
		LocalPlayer p = Minecraft.getInstance().player;
		HumanoidArm main = p.getMainArm();
		boolean using = p.isUsingItem();
		boolean blocking = p.isBlocking();
		HumanoidArm usedArm = !using ? null : (p.getUsedItemHand() == InteractionHand.MAIN_HAND ? main : main.getOpposite());

		for (HumanoidArm arm : new HumanoidArm[]{HumanoidArm.RIGHT, HumanoidArm.LEFT}) {
			ModelPart part = arm == HumanoidArm.RIGHT ? m.rightArm : m.leftArm;
			float s = arm == HumanoidArm.RIGHT ? 1f : -1f;
			ItemStack stack = p.getItemInHand(arm == main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
			boolean usedThis = using && arm == usedArm;

			if (usedThis && !blocking) continue;                 // yemek/icmek/yay/spyglass: vanilla poz
			if (stack.is(Items.CROSSBOW)) continue;              // arbalet: vanilla poz

			float rx, ry = 0f, rz = 0f;
			boolean touch = true;
			if (!stack.isEmpty()) { rx = READY_ITEM_X; ry = READY_ITEM_Y * s; }
			else if (arm == main) { rx = READY_FIST_X; ry = READY_FIST_Y * s; }
			else { rx = 0; touch = false; }

			// hafif yuruyus salinimi
			rx += 0.06f * Mth.cos(st.walkAnimationPos * 0.6662f + (s > 0 ? Mth.PI : 0f)) * st.walkAnimationSpeed;

			if (usedThis && blocking) {
				// KALKAN: tikladikca yumusakca kalkar, hasar alinca geri tepme
				float r = Mth.clamp(st.ticksUsingItem / 6f, 0f, 1f);
				r = r * r * (3f - 2f * r);
				rx = Mth.lerp(r, rx, SHIELD_X);
				ry = Mth.lerp(r, ry, SHIELD_Y * s);
				rz = 0f;
				rx -= 0.22f * Mth.clamp(p.hurtTime / 10f, 0f, 1f);
				touch = true;
			} else if (st.attackTime > 0f && arm == st.attackArm) {
				ItemStack swung = p.getItemInHand(arm == main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
				float[] r = swing(category(swung), st.attackTime, s);
				rx = r[0]; ry = r[1]; rz = r[2];
				touch = true;
			}

			if (touch) { part.xRot = rx; part.yRot = ry; part.zRot = rz; }
		}
	}

	// ---- esya kategorileri ----
	private static final int FIST = 0, SWORD = 1, AXE = 2, PICK = 3, SHOVEL = 4, HOE = 5, BLOCK = 6, OTHER = 7;

	private static int category(ItemStack st) {
		if (st.isEmpty()) return FIST;
		if (st.is(ItemTags.SWORDS)) return SWORD;
		if (st.is(ItemTags.AXES)) return AXE;
		if (st.is(ItemTags.PICKAXES)) return PICK;
		if (st.is(ItemTags.SHOVELS)) return SHOVEL;
		if (st.is(ItemTags.HOES)) return HOE;
		if (st.getItem() instanceof BlockItem) return BLOCK;
		return OTHER;
	}

	private static float smooth(float x) { x = Mth.clamp(x, 0f, 1f); return x * x * (3f - 2f * x); }

	/** kaldir (0..0.35) -> hizli indir (0.35..0.55) -> toparlan (0.55..1). Deger 0..1 (hafif eksiye tasar). */
	private static float chop(float u) {
		if (u < 0.35f) return smooth(u / 0.35f);
		if (u < 0.55f) return 1f - 1.2f * smooth((u - 0.35f) / 0.2f);
		return -0.2f * (1f - smooth((u - 0.55f) / 0.45f));
	}

	/** u = swing ilerlemesi (0..1), s = +1 sag kol / -1 sol kol. Donus: {xRot, yRot, zRot} */
	private static float[] swing(int cat, float u, float s) {
		float a = Mth.sin(u * Mth.PI);                 // 0 -> 1 -> 0
		float e = 1f - (1f - u) * (1f - u);            // ease-out
		return switch (cat) {
			case FIST   -> new float[]{-0.95f - 0.62f * a, -0.10f * s * (1f - a), 0f};                         // duz yumruk
			case SWORD  -> new float[]{-1.15f - 0.45f * a, s * (-0.90f + 1.90f * e), s * 0.35f * a};          // capraz kesis
			case AXE    -> new float[]{READY_ITEM_X - 1.90f * chop(u), -0.15f * s, s * 0.10f * chop(u)};      // yukaridan balta
			case PICK   -> new float[]{READY_ITEM_X - 1.60f * chop(u), -0.10f * s, 0f};                        // kazma darbesi
			case SHOVEL -> new float[]{-0.70f - 1.00f * a, -0.20f * s, s * 0.15f * a};                         // kurek: kaldirma
			case HOE    -> new float[]{-1.35f - 0.30f * a, s * (-0.30f + 1.10f * a), -s * 0.25f * a};          // capa: cekme
			case BLOCK  -> new float[]{-0.85f - 0.45f * a, -0.10f * s, 0f};                                    // blok koyma itme
			default     -> new float[]{-0.80f - 0.90f * a, -0.15f * s, 0f};
		};
	}
}
