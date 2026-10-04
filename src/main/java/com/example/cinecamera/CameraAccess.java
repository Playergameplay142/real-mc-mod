package com.example.cinecamera;

import net.minecraft.client.Camera;
import java.lang.reflect.Method;

/** Camera.setPosition / setRotation protected olabilir ve imzalari surumler arasi degisebilir; reflection ile guvenli cagri. */
final class CameraAccess {
	private static Method pos3, rot2, rot3;

	static {
		for (Class<?> c = Camera.class; c != null; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				Class<?>[] p = m.getParameterTypes();
				if (m.getName().equals("setPosition") && p.length == 3 && p[0] == double.class && p[1] == double.class && p[2] == double.class) pos3 = open(m);
				if (m.getName().equals("setRotation") && p.length == 2 && p[0] == float.class && p[1] == float.class) rot2 = open(m);
				if (m.getName().equals("setRotation") && p.length == 3 && p[0] == float.class && p[1] == float.class && p[2] == float.class) rot3 = open(m);
			}
		}
	}

	private static Method open(Method m) { m.setAccessible(true); return m; }

	static boolean available() { return pos3 != null && (rot2 != null || rot3 != null); }

	static void set(Camera cam, double x, double y, double z, float yaw, float pitch) throws Exception {
		if (rot2 != null) rot2.invoke(cam, yaw, pitch); else rot3.invoke(cam, yaw, pitch, 0f);
		pos3.invoke(cam, x, y, z);
	}
}
