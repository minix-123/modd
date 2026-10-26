package pl.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Zbiera statystyki:
 *  - zgony (wykrywane po zdrowiu gracza),
 *  - kto zabił (z wiadomości o śmierci, a gdy jej brak: ostatni gracz, który uderzył),
 *  - kto uderzył (gracz w pobliżu, który w chwili otrzymania obrażeń macha ręką w moją stronę),
 *  - zabójstwa (wiadomość o śmierci, w której zabójcą jestem ja).
 */
public final class StatsTracker {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static StatsData data = new StatsData();
	private static String loadedKey = null;

	private static boolean wasDead = false;
	private static int prevHurtTime = 0;

	private static String lastHitter = null;
	private static long lastHitTime = 0;

	// wiadomość o śmierci, która przyszła zanim wykryliśmy zgon
	private static boolean msgValid = false;
	private static String msgKiller = null;
	private static long msgTime = 0;

	// zgon policzony z ticka; wiadomość może jeszcze dojść i poprawić zabójcę
	private static long pendingDeathTime = -1;
	private static String pendingDeathKiller = null;
	private static boolean pendingGuess = false;

	private StatsTracker() {}

	// ------------------------------------------------------------------ dostęp dla GUI

	public static StatsData data() {
		return data;
	}

	public static String currentServerKey() {
		return loadedKey == null ? "-" : loadedKey;
	}

	// ------------------------------------------------------------------ tick

	public static void tick(Minecraft client) {
		LocalPlayer p = client.player;
		if (p == null || client.level == null) {
			wasDead = false;
			prevHurtTime = 0;
			return;
		}
		ensureLoaded(client);

		while (StatsClient.openKey.consumeClick()) {
			if (client.gui.screen() == null) {
				client.gui.setScreen(new StatsScreen());
			}
		}

		boolean dead = p.getHealth() <= 0.0f;
		if (dead && !wasDead) {
			onDeath();
		}
		wasDead = dead;

		if (!dead) {
			int ht = p.hurtTime;
			if (ht > 0 && prevHurtTime == 0) {
				onHurt(client, p);
			}
			prevHurtTime = ht;
		} else {
			prevHurtTime = 0;
		}
	}

	private static void onHurt(Minecraft client, LocalPlayer me) {
		Vec3 target = me.getBoundingBox().getCenter();
		AbstractClientPlayer best = null;
		double bestDist = Double.MAX_VALUE;

		for (AbstractClientPlayer other : client.level.players()) {
			if (other.getUUID().equals(me.getUUID())) continue;
			double d = other.distanceTo(me);
			if (d > 6.5) continue;
			if (!other.swinging) continue;
			Vec3 look = other.getLookAngle();
			Vec3 toMe = target.subtract(other.getEyePosition()).normalize();
			if (look.dot(toMe) < 0.6) continue;
			if (d < bestDist) {
				bestDist = d;
				best = other;
			}
		}

		if (best != null) {
			String name = best.getName().getString();
			data.hits.merge(name, 1, Integer::sum);
			FaceUtil.remember(name);
			lastHitter = name;
			lastHitTime = System.currentTimeMillis();
			save();
		}
	}

	private static void onDeath() {
		long now = System.currentTimeMillis();
		data.deaths++;

		String killer = null;
		boolean guess = false;
		if (msgValid && now - msgTime < 3000) {
			killer = msgKiller;
		} else if (lastHitter != null && now - lastHitTime < 10000) {
			killer = lastHitter;
			guess = true;
		} else {
			guess = true;
		}
		msgValid = false;

		if (killer != null) {
			data.killedBy.merge(killer, 1, Integer::sum);
			FaceUtil.remember(killer);
		}
		pendingDeathTime = now;
		pendingDeathKiller = killer;
		pendingGuess = guess;
		save();
	}

	// ------------------------------------------------------------------ wiadomości o śmierci

	public static void onGameMessage(Component message, boolean overlay) {
		if (overlay) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;

		TranslatableContents tc = findDeath(message);
		if (tc == null) return;
		Object[] args = tc.getArgs();
		if (args.length == 0) return;

		ensureLoaded(client);
		String me = client.player.getName().getString();
		String victim = argString(args[0]);
		String other = args.length > 1 ? argString(args[1]) : null;
		long now = System.currentTimeMillis();

		if (isMe(victim, me)) {
			String killer = other == null ? null : resolvePlayer(client, other);
			if (pendingDeathTime > 0 && now - pendingDeathTime < 3000) {
				if (pendingGuess) {
					if (pendingDeathKiller != null) dec(data.killedBy, pendingDeathKiller);
					if (killer != null) {
						data.killedBy.merge(killer, 1, Integer::sum);
						FaceUtil.remember(killer);
					}
					pendingDeathKiller = killer;
					pendingGuess = false;
				}
			} else {
				msgKiller = killer;
				msgTime = now;
				msgValid = true;
			}
			save();
		} else if (other != null && isMe(other, me)) {
			data.kills++;
			save();
		}
	}

	private static TranslatableContents findDeath(Component t) {
		if (t.getContents() instanceof TranslatableContents tc && tc.getKey().startsWith("death.")) {
			return tc;
		}
		for (Component sibling : t.getSiblings()) {
			TranslatableContents r = findDeath(sibling);
			if (r != null) return r;
		}
		return null;
	}

	private static String argString(Object o) {
		if (o instanceof Component c) return c.getString();
		return String.valueOf(o);
	}

	private static String strip(String s) {
		return s == null ? "" : s.replaceAll("§.", "");
	}

	private static boolean isMe(String s, String me) {
		for (String token : strip(s).split("\\s+")) {
			if (token.equalsIgnoreCase(me)) return true;
		}
		return false;
	}

	/** Zwraca nick gracza (z listy graczy na serwerze) albo null, jeśli to nie gracz (np. mob). */
	private static String resolvePlayer(Minecraft client, String s) {
		if (client.getConnection() == null) return null;
		for (String token : strip(s).split("\\s+")) {
			if (client.getConnection().getPlayerInfo(token) != null) return token;
		}
		return null;
	}

	private static void dec(Map<String, Integer> map, String key) {
		Integer v = map.get(key);
		if (v == null) return;
		if (v <= 1) map.remove(key);
		else map.put(key, v - 1);
	}

	// ------------------------------------------------------------------ zapis / odczyt

	private static String serverKey(Minecraft client) {
		ServerData sd = client.getCurrentServer();
		if (sd != null && sd.ip != null) return sd.ip;
		return "singleplayer";
	}

	private static Path fileFor(String key) {
		String safe = key.replaceAll("[^a-zA-Z0-9._-]", "_");
		return FabricLoader.getInstance().getConfigDir().resolve("stats").resolve(safe + ".json");
	}

	private static void ensureLoaded(Minecraft client) {
		String key = serverKey(client);
		if (key.equals(loadedKey)) return;
		save();
		loadedKey = key;
		data = new StatsData();
		Path f = fileFor(key);
		if (Files.exists(f)) {
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				StatsData loaded = GSON.fromJson(r, StatsData.class);
				if (loaded != null) {
					if (loaded.hits == null) loaded.hits = new LinkedHashMap<>();
					if (loaded.killedBy == null) loaded.killedBy = new LinkedHashMap<>();
					data = loaded;
				}
			} catch (IOException | RuntimeException e) {
				e.printStackTrace();
			}
		}
		wasDead = false;
		lastHitter = null;
		msgValid = false;
		pendingDeathTime = -1;
	}

	public static void save() {
		if (loadedKey == null) return;
		Path f = fileFor(loadedKey);
		try {
			Files.createDirectories(f.getParent());
			try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
				GSON.toJson(data, w);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
