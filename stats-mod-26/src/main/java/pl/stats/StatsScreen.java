package pl.stats;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Neonowe okno otwierane klawiszem B: zakładki po lewej (Profil / Hit / Kill). */
public class StatsScreen extends Screen {
	private enum Tab {
		PROFIL("PROFIL", 0xFF00F0FF),
		HIT("HIT", 0xFFFF2BD6),
		KILL("KILL", 0xFFFF3860);

		final String label;
		final int accent;

		Tab(String label, int accent) {
			this.label = label;
			this.accent = accent;
		}
	}

	// kolory (ARGB)
	private static final int BG = 0xF00A0A18;
	private static final int SIDEBAR = 0xF0070712;
	private static final int CARD = 0xFF12122A;
	private static final int ROW = 0xAA14142E;
	private static final int WHITE = 0xFFEAF6FF;
	private static final int DIM = 0xFF7F8CB0;
	private static final int GREEN = 0xFF39FF88;
	private static final int PURPLE = 0xFFB45CFF;

	private static final int SIDEBAR_W = 90;
	private static final int TAB_H = 24;
	private static final int ROW_H = 20;

	private Tab tab = Tab.PROFIL;
	private double scroll = 0;

	// układ okna (liczony w layout())
	private int px, py, pw, ph;

	public StatsScreen() {
		super(Component.literal("Stats"));
	}

	private void layout() {
		pw = Math.min(400, this.width - 16);
		ph = Math.min(236, this.height - 16);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
	}

	@Override
	protected void init() {
		layout();
		int i = 0;
		for (Tab t : Tab.values()) {
			// niewidoczne przyciski tylko obsługują kliknięcia; wygląd rysujemy sami
			Button b = Button.builder(Component.empty(), btn -> {
				tab = t;
				scroll = 0;
			}).bounds(px + 6, py + 44 + i * (TAB_H + 4), SIDEBAR_W - 12, TAB_H).build();
			this.addRenderableWidget(b);
			i++;
		}
	}

	// bez @Override, żeby ewentualna zmiana sygnatury w nowszych wersjach nie psuła kompilacji
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll -= verticalAmount;
		return true;
	}

	// ------------------------------------------------------------------ pomocnicze rysowanie

	private static int alpha(int color, int a) {
		return (a << 24) | (color & 0xFFFFFF);
	}

	/** Ramka o grubości 1 px. */
	private static void border(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		g.fill(x0, y0, x1, y0 + 1, color);
		g.fill(x0, y1 - 1, x1, y1, color);
		g.fill(x0, y0, x0 + 1, y1, color);
		g.fill(x1 - 1, y0, x1, y1, color);
	}

	/** Neonowa ramka: jasna linia + kilka słabnących warstw "poświaty" na zewnątrz. */
	private static void neonBorder(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		for (int i = 4; i >= 1; i--) {
			border(g, x0 - i, y0 - i, x1 + i, y1 + i, alpha(color, 0x38 / i));
		}
		border(g, x0, y0, x1, y1, color);
	}

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ------------------------------------------------------------------ rysowanie

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		layout();

		int accent = tab.accent;

		// tło okna + poświata
		neonBorder(g, px, py, px + pw, py + ph, accent);
		g.fill(px, py, px + pw, py + ph, BG);

		// panel boczny
		g.fill(px + 1, py + 1, px + SIDEBAR_W, py + ph - 1, SIDEBAR);
		g.fill(px + SIDEBAR_W, py + 1, px + SIDEBAR_W + 1, py + ph - 1, alpha(accent, 0xAA));
		g.fill(px + SIDEBAR_W + 1, py + 1, px + SIDEBAR_W + 2, py + ph - 1, alpha(accent, 0x30));

		// logo "STATS" z neonowym cieniem
		String logo = "STATS";
		int lx = px + (SIDEBAR_W - this.font.width(logo)) / 2;
		g.text(this.font, logo, lx + 1, py + 15, alpha(Tab.HIT.accent, 0xCC), false);
		g.text(this.font, logo, lx, py + 14, Tab.PROFIL.accent, false);
		g.fill(px + 14, py + 30, px + SIDEBAR_W - 14, py + 31, alpha(PURPLE, 0x90));

		// zakładki
		int i = 0;
		for (Tab t : Tab.values()) {
			int bx = px + 6;
			int by = py + 44 + i * (TAB_H + 4);
			int bw = SIDEBAR_W - 12;
			boolean active = t == tab;
			boolean hover = inside(mouseX, mouseY, bx, by, bw, TAB_H);

			if (active) {
				g.fill(bx, by, bx + bw, by + TAB_H, alpha(t.accent, 0x30));
				border(g, bx, by, bx + bw, by + TAB_H, alpha(t.accent, 0xCC));
				g.fill(bx, by, bx + 3, by + TAB_H, t.accent);
				g.fill(bx - 2, by, bx, by + TAB_H, alpha(t.accent, 0x50));
			} else if (hover) {
				g.fill(bx, by, bx + bw, by + TAB_H, alpha(t.accent, 0x18));
				border(g, bx, by, bx + bw, by + TAB_H, alpha(t.accent, 0x70));
			} else {
				g.fill(bx, by, bx + bw, by + TAB_H, 0xFF0C0C1C);
				border(g, bx, by, bx + bw, by + TAB_H, 0xFF1C1C38);
			}

			int color = active ? t.accent : (hover ? WHITE : DIM);
			g.text(this.font, t.label, bx + 12, by + (TAB_H - 8) / 2, color, false);
			i++;
		}

		// podpowiedź na dole panelu bocznego
		String hint = "ESC - zamknij";
		g.text(this.font, hint, px + (SIDEBAR_W - this.font.width(hint)) / 2, py + ph - 14, alpha(DIM, 0xAA), false);

		// ---------------------------------------------------------- zawartość
		int cx = px + SIDEBAR_W + 14;
		int cw = pw - SIDEBAR_W - 28;

		g.text(this.font, tab.label, cx + 1, py + 13, alpha(accent, 0x60), false);
		g.text(this.font, tab.label, cx, py + 12, accent, false);

		String server = StatsTracker.currentServerKey();
		if (server.length() > 34) server = server.substring(0, 33) + "...";
		g.text(this.font, server, cx + cw - this.font.width(server), py + 12, DIM, false);

		g.fill(cx, py + 26, cx + cw, py + 27, alpha(accent, 0xAA));
		g.fill(cx, py + 27, cx + cw, py + 28, alpha(accent, 0x28));

		StatsData d = StatsTracker.data();
		int top = py + 38;
		int bottom = py + ph - 10;

		switch (tab) {
			case PROFIL -> drawProfile(g, d, cx, top, cw);
			case HIT -> drawRanking(g, d.hits, "Nikt jeszcze Cię nie uderzył.", cx, top, bottom, cw, Tab.HIT.accent);
			case KILL -> drawRanking(g, d.killedBy, "Nikt jeszcze Cię nie zabił.", cx, top, bottom, cw, Tab.KILL.accent);
		}
	}

	private void drawProfile(GuiGraphicsExtractor g, StatsData d, int cx, int top, int cw) {
		int hitsTotal = d.hits.values().stream().mapToInt(Integer::intValue).sum();
		double kd = d.deaths == 0 ? d.kills : (double) d.kills / d.deaths;

		int gap = 8;
		int cardW = (cw - gap) / 2;
		int cardH = 52;

		drawCard(g, cx, top, cardW, cardH, "ZGONY", String.valueOf(d.deaths), Tab.KILL.accent);
		drawCard(g, cx + cardW + gap, top, cardW, cardH, "ZABÓJSTWA", String.valueOf(d.kills), GREEN);
		drawCard(g, cx, top + cardH + gap, cardW, cardH, "K/D",
			String.format(Locale.ROOT, "%.2f", kd), Tab.PROFIL.accent);
		drawCard(g, cx + cardW + gap, top + cardH + gap, cardW, cardH, "UDERZENIA", String.valueOf(hitsTotal),
			Tab.HIT.accent);

		String note = "Statystyki dotyczą tylko tego serwera.";
		g.text(this.font, note, cx, top + 2 * cardH + gap + 12, alpha(DIM, 0xCC), false);
	}

	private void drawCard(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String value, int color) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, alpha(color, 0x18));
		g.fill(x, y, x + w, y + h, CARD);
		border(g, x, y, x + w, y + h, alpha(color, 0x90));
		g.fill(x, y, x + w, y + 2, color);
		g.fill(x, y + 2, x + w, y + 4, alpha(color, 0x30));
		g.text(this.font, label, x + 10, y + 12, DIM, false);
		// wartość "pogrubiona" dwukrotnym rysowaniem + poświata
		g.text(this.font, value, x + 11, y + 29, alpha(color, 0x60), false);
		g.text(this.font, value, x + 10, y + 28, color, false);
		g.text(this.font, value, x + 11, y + 28, color, false);
	}

	private void drawRanking(GuiGraphicsExtractor g, Map<String, Integer> map, String emptyText,
							 int cx, int top, int bottom, int cw, int color) {
		if (map.isEmpty()) {
			g.fill(cx, top, cx + cw, top + 40, CARD);
			border(g, cx, top, cx + cw, top + 40, alpha(color, 0x60));
			g.text(this.font, emptyText, cx + 12, top + 16, DIM, false);
			return;
		}

		List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
		entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		int max = entries.get(0).getValue();

		int visible = Math.max(1, (bottom - top - 12) / (ROW_H + 3));
		int maxScroll = Math.max(0, entries.size() - visible);
		if (scroll < 0) scroll = 0;
		if (scroll > maxScroll) scroll = maxScroll;

		int y = top;
		int start = (int) scroll;
		for (int i = start; i < entries.size() && i < start + visible; i++) {
			Map.Entry<String, Integer> e = entries.get(i);
			int count = e.getValue();

			g.fill(cx, y, cx + cw, y + ROW_H, ROW);
			int barW = Math.max(2, (int) ((cw - 2) * (count / (double) max)));
			g.fill(cx + 1, y + 1, cx + 1 + barW - 1, y + ROW_H - 1, alpha(color, 0x38));
			g.fill(cx, y, cx + 2, y + ROW_H, color);

			String rank = "#" + (i + 1);
			g.text(this.font, rank, cx + 8, y + 6, i == 0 ? color : DIM, false);
			g.text(this.font, e.getKey(), cx + 8 + 24, y + 6, WHITE, false);

			String num = count + "x";
			g.text(this.font, num, cx + cw - 8 - this.font.width(num), y + 6, color, false);
			y += ROW_H + 3;
		}

		if (maxScroll > 0) {
			String more = "kółko myszy - przewijanie (" + (start + 1) + "-" + Math.min(entries.size(), start + visible)
				+ " z " + entries.size() + ")";
			g.text(this.font, more, cx, bottom - 8, alpha(DIM, 0xAA), false);
		}
	}
}
