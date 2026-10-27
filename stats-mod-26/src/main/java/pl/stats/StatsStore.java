package pl.stats;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dane jednego serwera: kilka profili (np. różne tryby gry), każdy z osobnymi statystykami. */
public class StatsStore {
	public static final String DEFAULT_NAME = "Domyślny";

	/** Nazwa aktywnego profilu. */
	public String active = DEFAULT_NAME;
	/** Nazwa profilu -> statystyki. */
	public Map<String, StatsData> profiles = new LinkedHashMap<>();

	public StatsStore() {
		profiles.put(DEFAULT_NAME, new StatsData());
	}

	/** Naprawia dane po wczytaniu z pliku (braki, pusta lista, zły aktywny profil). */
	public void fix() {
		if (profiles == null) profiles = new LinkedHashMap<>();
		for (Map.Entry<String, StatsData> e : profiles.entrySet()) {
			StatsData d = e.getValue();
			if (d == null) {
				d = new StatsData();
				e.setValue(d);
			}
			if (d.hits == null) d.hits = new LinkedHashMap<>();
			if (d.killedBy == null) d.killedBy = new LinkedHashMap<>();
		}
		if (profiles.isEmpty()) profiles.put(DEFAULT_NAME, new StatsData());
		if (active == null || !profiles.containsKey(active)) active = profiles.keySet().iterator().next();
	}
}
