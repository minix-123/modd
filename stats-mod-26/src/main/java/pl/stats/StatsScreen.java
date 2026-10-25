package pl.stats;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Okno otwierane klawiszem B: zakładki Profil / Hit / Kill. */
public class StatsScreen extends Screen {
	private enum Tab {
		PROFIL("Profil"),
		HIT("Hit"),
		KILL("Kill");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private record Line(String text, int color) {}

	private static final int W = 320;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int YELLOW = 0xFFFFD84D;

	private Tab tab = Tab.PROFIL;
	private double scroll = 0;
	private final List<Button> tabButtons = new ArrayList<>();

	public StatsScreen() {
		super(Component.literal("Stats"));
	}

	@Override
	protected void init() {
		tabButtons.clear();
		int x0 = (this.width - W) / 2;
		int gap = 6;
		int bw = (W - 2 * gap) / 3;
		int i = 0;
		for (Tab t : Tab.values()) {
			Button b = Button.builder(Component.literal(t.label), btn -> {
				tab = t;
				scroll = 0;
				refreshButtons();
			}).bounds(x0 + i * (bw + gap), 24, bw, 20).build();
			tabButtons.add(b);
			this.addRenderableWidget(b);
			i++;
		}
		refreshButtons();
	}

	private void refreshButtons() {
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabButtons.size(); i++) {
			tabButtons.get(i).active = tabs[i] != tab;
		}
	}

	// bez @Override, żeby ewentualna zmiana sygnatury w nowszych wersjach nie psuła kompilacji
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll -= verticalAmount;
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int x0 = (this.width - W) / 2;
		int top = 52;
		int bottom = this.height - 16;

		String title = "Stats - " + StatsTracker.currentServerKey();
		graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 10, WHITE, true);

		graphics.fill(x0, top, x0 + W, bottom, 0xAA000000);

		List<Line> lines = buildLines();
		int lineHeight = 12;
		int visible = Math.max(1, (bottom - top - 8) / lineHeight);
		int maxScroll = Math.max(0, lines.size() - visible);
		if (scroll < 0) scroll = 0;
		if (scroll > maxScroll) scroll = maxScroll;

		int y = top + 4;
		for (int i = (int) scroll; i < lines.size() && i < (int) scroll + visible; i++) {
			Line l = lines.get(i);
			graphics.text(this.font, l.text(), x0 + 8, y, l.color(), false);
			y += lineHeight;
		}
	}

	private List<Line> buildLines() {
		StatsData d = StatsTracker.data();
		List<Line> out = new ArrayList<>();

		switch (tab) {
			case PROFIL -> {
				int hitsTotal = d.hits.values().stream().mapToInt(Integer::intValue).sum();
				double kd = d.deaths == 0 ? d.kills : (double) d.kills / d.deaths;
				out.add(new Line("Serwer: " + StatsTracker.currentServerKey(), GRAY));
				out.add(new Line("", WHITE));
				out.add(new Line("Zginąłem: " + d.deaths + " razy", WHITE));
				out.add(new Line("Zabiłem graczy: " + d.kills, WHITE));
				out.add(new Line("K/D: " + String.format(Locale.ROOT, "%.2f", kd), WHITE));
				out.add(new Line("Otrzymane uderzenia od graczy: " + hitsTotal, WHITE));
			}
			case HIT -> {
				out.add(new Line("Gracze, którzy mnie uderzyli:", YELLOW));
				addRanking(out, d.hits);
			}
			case KILL -> {
				out.add(new Line("Gracze, którzy mnie zabili:", YELLOW));
				addRanking(out, d.killedBy);
			}
		}
		return out;
	}

	private static void addRanking(List<Line> out, Map<String, Integer> map) {
		if (map.isEmpty()) {
			out.add(new Line("Brak danych.", GRAY));
			return;
		}
		List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
		entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		int n = 1;
		for (Map.Entry<String, Integer> e : entries) {
			out.add(new Line(n + ". " + e.getKey() + "  -  " + e.getValue() + "x", WHITE));
			n++;
		}
	}
}
