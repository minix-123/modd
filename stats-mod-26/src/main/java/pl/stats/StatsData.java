package pl.stats;

import java.util.LinkedHashMap;
import java.util.Map;

/** Statystyki zapisywane osobno dla każdego serwera. */
public class StatsData {
	/** Ile razy zginąłem. */
	public int deaths = 0;
	/** Ilu graczy zabiłem. */
	public int kills = 0;
	/** Nick -> ile razy mnie uderzył. */
	public Map<String, Integer> hits = new LinkedHashMap<>();
	/** Nick -> ile razy mnie zabił. */
	public Map<String, Integer> killedBy = new LinkedHashMap<>();
}
