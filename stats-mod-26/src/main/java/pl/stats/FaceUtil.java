package pl.stats;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Rysowanie głów graczy. Metoda rysująca twarz jest wyszukiwana w czasie działania gry,
 * dzięki czemu zmiany nazw w nowych wersjach Minecrafta nie psują kompilacji.
 * Gdy się nie uda, GUI rysuje zamiast głowy kolorowy awatar z literą.
 */
public final class FaceUtil {
	private static final Map<String, Object> SKINS = new HashMap<>();
	private static Method faceMethod = null;
	private static boolean searched = false;

	private FaceUtil() {}

	/** Zapamiętuje skórkę gracza (póki jest na serwerze), żeby głowa była widoczna także po jego wyjściu. */
	public static void remember(String name) {
		if (name != null) lookup(name);
	}

	private static Object lookup(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.getConnection() != null) {
				Object info = mc.getConnection().getPlayerInfo(name);
				if (info != null) {
					Object skin = skinOf(info);
					if (skin != null) {
						SKINS.put(key, skin);
						return skin;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return SKINS.get(key);
	}

	private static Object skinOf(Object info) {
		try {
			for (Method m : info.getClass().getMethods()) {
				if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals("PlayerSkin")) {
					return m.invoke(info);
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static void findFaceMethod() {
		searched = true;
		String[] classes = {
			"net.minecraft.client.gui.components.PlayerFaceExtractor",
			"net.minecraft.client.gui.components.PlayerFaceRenderer",
			"net.minecraft.client.gui.PlayerFaceExtractor",
			"net.minecraft.client.gui.PlayerFaceRenderer"
		};
		for (String cn : classes) {
			try {
				Class<?> c = Class.forName(cn);
				for (Method m : c.getMethods()) {
					Class<?>[] p = m.getParameterTypes();
					if (!Modifier.isStatic(m.getModifiers())) continue;
					if (p.length != 5) continue;
					if (!p[0].isAssignableFrom(GuiGraphicsExtractor.class)) continue;
					if (!p[1].getSimpleName().equals("PlayerSkin")) continue;
					if (p[2] != int.class || p[3] != int.class || p[4] != int.class) continue;
					faceMethod = m;
					return;
				}
			} catch (Throwable ignored) {
			}
		}
	}

	/** Rysuje głowę gracza (kwadrat size x size). Zwraca false, jeśli się nie udało. */
	public static boolean drawFace(GuiGraphicsExtractor g, String name, int x, int y, int size) {
		if (!searched) findFaceMethod();
		if (faceMethod == null) return false;
		Object skin = lookup(name);
		if (skin == null) return false;
		try {
			faceMethod.invoke(null, g, skin, x, y, size);
			return true;
		} catch (Throwable t) {
			faceMethod = null; // nie próbuj w kółko, jeśli coś nie działa
			return false;
		}
	}
}
