package pl.stats;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Neonowe okno otwierane klawiszem B: zakładki po lewej (Profil / Hit / Kill / Ustawienia). */
public class StatsScreen extends Screen {
	private enum Tab {
		PROFIL("PROFIL", 0xFF00F0FF),
		HIT("HIT", 0xFFFF2BD6),
		KILL("KILL", 0xFFFF3860),
		USTAWIENIA("USTAWIENIA", 0xFFB45CFF);

		final String label;
		final int accent;

		Tab(String label, int accent) {
			this.label = label;
			this.accent = accent;
		}
	}

	// kolory (ARGB)
	private static final int BG_TOP = 0xF0130E2E;
	private static final int BG_BOTTOM = 0xF0060612;
	private static final int SIDEBAR = 0xF0070712;
	private static final int BASE = 0xFF0B0B1A;
	private static final int CARD = 0xFF10102A;
	private static final int ROW = 0xB0141432;
	private static final int WHITE = 0xFFEAF6FF;
	private static final int DIM = 0xFF7F8CB0;
	private static final int GREEN = 0xFF39FF88;
	private static final int RED = 0xFFFF3860;
	private static final int PURPLE = 0xFFB45CFF;

	private static final int SIDEBAR_W = 108;
	private static final int TAB_H = 28;
	private static final int ROW_H = 30;
	private static final int ROW_GAP = 4;

	// ustawienia
	private static final int PROF_ROWS = 3;
	private static final int PROF_ROW_H = 22;
	private static final int PROF_ROW_GAP = 2;

	private Tab tab = Tab.PROFIL;
	private double scroll = 0;

	private int px, py, pw, ph;

	private boolean needsRebuild = true;
	private int builtStart = -1;

	private boolean addMode = false;
	private boolean confirmDelete = false;
	private String msg = "";
	private int msgColor = DIM;

	// pole tekstowe (tworzone refleksją - gdyby się nie udało, nazwa profilu jest nadawana automatycznie)
	private AbstractWidget nameBox = null;
	private boolean boxFailed = false;
	private String nameText = "";

	public StatsScreen() {
		super(Component.literal("Stats"));
	}

	private void layout() {
		pw = Math.min(440, this.width - 16);
		ph = Math.min(256, this.height - 16);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
	}

	@Override
	protected void init() {
		needsRebuild = true;
	}

	// ------------------------------------------------------------------ widgety (niewidoczne przyciski do klikania)

	private void hidden(int x, int y, int w, int h, Runnable action) {
		Button b = Button.builder(Component.empty(), btn -> action.run()).bounds(x, y, w, h).build();
		try {
			b.getClass().getMethod("setAlpha", float.class).invoke(b, 0.0f);
		} catch (Throwable ignored) {
			// zasłaniamy je nieprzezroczystym tłem
		}
		this.addRenderableWidget(b);
	}

	private int cx() {
		return px + SIDEBAR_W + 16;
	}

	private int cw() {
		return pw - SIDEBAR_W - 32;
	}

	private int top() {
		return py + 46;
	}

	// geometria ustawień
	private int listY() {
		return top() + 14;
	}

	private int btnY() {
		return top() + 96;
	}

	private int addY() {
		return top() + 124;
	}

	private int fieldY() {
		return top() + 135;
	}

	private int saveY() {
		return top() + 160;
	}

	private int msgY() {
		return top() + 186;
	}

	private int half() {
		return (cw() - 8) / 2;
	}

	private void rebuild() {
		needsRebuild = false;
		this.clearWidgets();
		layout();

		int i = 0;
		for (Tab t : Tab.values()) {
			final Tab target = t;
			hidden(px + 8, py + 50 + i * (TAB_H + 6), SIDEBAR_W - 16, TAB_H, () -> {
				tab = target;
				scroll = 0;
				addMode = false;
				confirmDelete = false;
				msg = "";
				needsRebuild = true;
			});
			i++;
		}

		if (tab == Tab.USTAWIENIA) buildSettings();
	}

	private void buildSettings() {
		int cx = cx();
		int cw = cw();
		List<String> names = StatsTracker.profileNames();

		int maxScroll = Math.max(0, names.size() - PROF_ROWS);
		if (scroll < 0) scroll = 0;
		if (scroll > maxScroll) scroll = maxScroll;
		builtStart = (int) scroll;

		for (int r = 0; r < PROF_ROWS; r++) {
			int idx = builtStart + r;
			if (idx >= names.size()) break;
			final String n = names.get(idx);
			hidden(cx, listY() + r * (PROF_ROW_H + PROF_ROW_GAP), cw, PROF_ROW_H, () -> {
				StatsTracker.selectProfile(n);
				confirmDelete = false;
				msg = "";
			});
		}

		hidden(cx, btnY(), half(), 22, this::onAddClicked);
		hidden(cx + half() + 8, btnY(), half(), 22, this::onDeleteClicked);

		if (addMode) {
			createNameBox(cx + 3, fieldY() + 3, cw - 6, 14);
			hidden(cx, saveY(), half(), 20, this::onSave);
			hidden(cx + half() + 8, saveY(), half(), 20, () -> {
				addMode = false;
				msg = "";
				needsRebuild = true;
			});
		}
	}

	private void onAddClicked() {
		confirmDelete = false;
		if (boxFailed) {
			// brak pola tekstowego: nazwa automatyczna
			StatsTracker.addProfile(StatsTracker.autoProfileName());
			msg = "Dodano profil: " + StatsTracker.activeProfile();
			msgColor = GREEN;
			needsRebuild = true;
			return;
		}
		addMode = true;
		nameText = "";
		msg = "";
		needsRebuild = true;
	}

	private void onSave() {
		String err = StatsTracker.addProfile(nameText);
		if (err != null) {
			msg = err;
			msgColor = RED;
		} else {
			msg = "Dodano i wybrano profil: " + StatsTracker.activeProfile();
			msgColor = GREEN;
			addMode = false;
			nameText = "";
			needsRebuild = true;
		}
	}

	private void onDeleteClicked() {
		if (!confirmDelete) {
			confirmDelete = true;
			msg = "Kliknij USUŃ PROFIL jeszcze raz, aby potwierdzić.";
			msgColor = RED;
		} else {
			String name = StatsTracker.activeProfile();
			StatsTracker.deleteProfile(name);
			confirmDelete = false;
			scroll = 0;
			msg = "Usunięto profil: " + name;
			msgColor = GREEN;
			needsRebuild = true;
		}
	}

	// ------------------------------------------------------------------ pole tekstowe (refleksja)

	private void createNameBox(int x, int y, int w, int h) {
		nameBox = null;
		if (boxFailed) return;
		try {
			Class<?> c = Class.forName("net.minecraft.client.gui.components.EditBox");
			for (Constructor<?> k : c.getConstructors()) {
				Class<?>[] p = k.getParameterTypes();
				if (p.length != 6) continue;
				if (!p[0].isInstance(this.font)) continue;
				if (p[1] != int.class || p[2] != int.class || p[3] != int.class || p[4] != int.class) continue;
				if (!p[5].isAssignableFrom(Component.class)) continue;
				Object o = k.newInstance(this.font, x, y, w, h, Component.empty());
				nameBox = (AbstractWidget) o;
				break;
			}
			if (nameBox == null) {
				boxFailed = true;
				return;
			}
			call(nameBox, "setMaxLength", int.class, 20);
			call(nameBox, "setValue", String.class, nameText);
			this.addRenderableWidget(nameBox);
			this.setFocused(nameBox);
			try {
				nameBox.setFocused(true);
			} catch (Throwable ignored) {
			}
		} catch (Throwable t) {
			nameBox = null;
			boxFailed = true;
			addMode = false;
		}
	}

	private static void call(Object target, String method, Class<?> type, Object arg) {
		try {
			Method m = target.getClass().getMethod(method, type);
			m.invoke(target, arg);
		} catch (Throwable ignored) {
		}
	}

	private String readNameBox() {
		if (nameBox == null) return nameText;
		try {
			Object v = nameBox.getClass().getMethod("getValue").invoke(nameBox);
			return v == null ? "" : v.toString();
		} catch (Throwable t) {
			return nameText;
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

	private static int lerp(int c1, int c2, float t) {
		int a = (int) (((c1 >>> 24) & 0xFF) * (1 - t) + ((c2 >>> 24) & 0xFF) * t);
		int r = (int) (((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
		int g = (int) (((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
		int b = (int) ((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static void border(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		g.fill(x0, y0, x1, y0 + 1, color);
		g.fill(x0, y1 - 1, x1, y1, color);
		g.fill(x0, y0, x0 + 1, y1, color);
		g.fill(x1 - 1, y0, x1, y1, color);
	}

	/** Neonowa ramka: jasna linia + słabnące warstwy "poświaty" na zewnątrz. */
	private static void neonBorder(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		for (int i = 5; i >= 1; i--) {
			border(g, x0 - i, y0 - i, x1 + i, y1 + i, alpha(color, 0x40 / i));
		}
		border(g, x0, y0, x1, y1, color);
	}

	/** Jasne narożniki w stylu "tech". */
	private static void corners(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int len, int color) {
		g.fill(x0, y0, x0 + len, y0 + 2, color);
		g.fill(x0, y0, x0 + 2, y0 + len, color);
		g.fill(x1 - len, y0, x1, y0 + 2, color);
		g.fill(x1 - 2, y0, x1, y0 + len, color);
		g.fill(x0, y1 - 2, x0 + len, y1, color);
		g.fill(x0, y1 - len, x0 + 2, y1, color);
		g.fill(x1 - len, y1 - 2, x1, y1, color);
		g.fill(x1 - 2, y1 - len, x1, y1, color);
	}

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private int bigWidth(String s, float scale) {
		return (int) (this.font.width(s) * scale);
	}

	/** Duży tekst (skalowanie przez macierz GUI, wywoływane refleksją - w razie błędu zwykły tekst). */
	private void bigText(GuiGraphicsExtractor g, String s, int x, int y, int color, float scale) {
		try {
			Object pose = g.getClass().getMethod("pose").invoke(g);
			Method push = pose.getClass().getMethod("pushMatrix");
			Method pop = pose.getClass().getMethod("popMatrix");
			Method sc = pose.getClass().getMethod("scale", float.class, float.class);
			push.invoke(pose);
			try {
				sc.invoke(pose, scale, scale);
				g.text(this.font, s, Math.round(x / scale), Math.round(y / scale), color, false);
			} finally {
				pop.invoke(pose);
			}
		} catch (Throwable t) {
			g.text(this.font, s, x, y, color, false);
			g.text(this.font, s, x + 1, y, color, false);
		}
	}

	/** Neonowy przycisk (rysowany nad niewidocznym przyciskiem, który odbiera kliknięcie). */
	private void drawButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h,
							String label, int color, boolean strong) {
		boolean hover = inside(mouseX, mouseY, x, y, w, h);
		g.fill(x, y, x + w, y + h, BASE);
		g.fill(x, y, x + w, y + h, alpha(color, strong ? 0x55 : (hover ? 0x35 : 0x1A)));
		border(g, x, y, x + w, y + h, alpha(color, hover || strong ? 0xFF : 0xA0));
		g.fill(x, y + h - 2, x + w, y + h, alpha(color, hover || strong ? 0xFF : 0x80));
		int tx = x + (w - this.font.width(label)) / 2;
		g.text(this.font, label, tx, y + (h - 8) / 2, hover || strong ? WHITE : color, false);
	}

	// ------------------------------------------------------------------ rysowanie

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		layout();
		if (tab == Tab.USTAWIENIA && (int) Math.max(0, scroll) != builtStart) needsRebuild = true;
		if (needsRebuild) rebuild();

		super.extractRenderState(g, mouseX, mouseY, delta);
		layout();

		if (addMode) nameText = readNameBox();

		int accent = tab.accent;

		// poświata + tło z gradientem
		neonBorder(g, px, py, px + pw, py + ph, accent);
		for (int i = 0; i < ph; i++) {
			g.fill(px, py + i, px + pw, py + i + 1, lerp(BG_TOP, BG_BOTTOM, i / (float) ph));
		}
		corners(g, px, py, px + pw, py + ph, 12, accent);

		// panel boczny
		g.fill(px + 2, py + 2, px + SIDEBAR_W, py + ph - 2, SIDEBAR);
		g.fill(px + SIDEBAR_W, py + 2, px + SIDEBAR_W + 1, py + ph - 2, alpha(accent, 0xB0));
		g.fill(px + SIDEBAR_W + 1, py + 2, px + SIDEBAR_W + 3, py + ph - 2, alpha(accent, 0x22));

		// logo
		String logo = "STATS";
		float ls = 1.6f;
		int lw = bigWidth(logo, ls);
		int lx = px + (SIDEBAR_W - lw) / 2;
		bigText(g, logo, lx + 1, py + 15, alpha(Tab.HIT.accent, 0xB0), ls);
		bigText(g, logo, lx, py + 14, Tab.PROFIL.accent, ls);
		g.fill(px + 14, py + 36, px + SIDEBAR_W - 14, py + 37, alpha(PURPLE, 0xA0));
		g.fill(px + 14, py + 37, px + SIDEBAR_W - 14, py + 38, alpha(PURPLE, 0x30));

		// zakładki
		int i = 0;
		for (Tab t : Tab.values()) {
			int bx = px + 8;
			int by = py + 50 + i * (TAB_H + 6);
			int bw = SIDEBAR_W - 16;
			boolean active = t == tab;
			boolean hover = inside(mouseX, mouseY, bx, by, bw, TAB_H);

			g.fill(bx - 2, by - 2, bx + bw + 2, by + TAB_H + 2, SIDEBAR);
			g.fill(bx, by, bx + bw, by + TAB_H, BASE);
			if (active) {
				g.fill(bx - 2, by - 2, bx + bw + 2, by + TAB_H + 2, alpha(t.accent, 0x18));
				g.fill(bx, by, bx + bw, by + TAB_H, alpha(t.accent, 0x30));
				border(g, bx, by, bx + bw, by + TAB_H, alpha(t.accent, 0xDD));
				g.fill(bx, by, bx + 4, by + TAB_H, t.accent);
			} else if (hover) {
				g.fill(bx, by, bx + bw, by + TAB_H, alpha(t.accent, 0x1C));
				border(g, bx, by, bx + bw, by + TAB_H, alpha(t.accent, 0x80));
				g.fill(bx, by, bx + 2, by + TAB_H, alpha(t.accent, 0xAA));
			} else {
				border(g, bx, by, bx + bw, by + TAB_H, 0xFF1C1C3A);
			}

			int dot = active ? t.accent : (hover ? alpha(t.accent, 0xCC) : alpha(t.accent, 0x60));
			g.fill(bx + 12, by + TAB_H / 2 - 3, bx + 18, by + TAB_H / 2 + 3, dot);

			int color = active ? t.accent : (hover ? WHITE : DIM);
			g.text(this.font, t.label, bx + 24, by + (TAB_H - 8) / 2, color, false);
			i++;
		}

		String hint = "ESC - zamknij";
		g.text(this.font, hint, px + (SIDEBAR_W - this.font.width(hint)) / 2, py + ph - 16, alpha(DIM, 0xAA), false);

		// ---------------------------------------------------------- zawartość
		int cx = cx();
		int cw = cw();

		bigText(g, tab.label, cx + 1, py + 13, alpha(accent, 0x60), 1.5f);
		bigText(g, tab.label, cx, py + 12, accent, 1.5f);

		String server = StatsTracker.currentServerKey();
		if (server.length() > 20) server = server.substring(0, 19) + "...";
		String prof = StatsTracker.activeProfile();
		if (prof.length() > 14) prof = prof.substring(0, 13) + "...";
		String serverLine = server + "  |  " + prof;
		g.text(this.font, serverLine, cx + cw - this.font.width(serverLine), py + 16, DIM, false);

		g.fill(cx, py + 32, cx + cw, py + 33, alpha(accent, 0xCC));
		g.fill(cx, py + 33, cx + cw, py + 35, alpha(accent, 0x25));

		StatsData d = StatsTracker.data();
		int top = top();
		int bottom = py + ph - 12;

		switch (tab) {
			case PROFIL -> drawProfile(g, d, cx, top, bottom, cw);
			case HIT -> drawRanking(g, d.hits, "Nikt jeszcze Cię nie uderzył.", cx, top, bottom, cw, Tab.HIT.accent);
			case KILL -> drawRanking(g, d.killedBy, "Nikt jeszcze Cię nie zabił.", cx, top, bottom, cw, Tab.KILL.accent);
			case USTAWIENIA -> drawSettings(g, mouseX, mouseY, cx, cw);
		}
	}

	// ------------------------------------------------------------------ ustawienia

	private void drawSettings(GuiGraphicsExtractor g, int mouseX, int mouseY, int cx, int cw) {
		int top = top();
		g.text(this.font, "WYBÓR PROFILU (osobne statystyki na każdy tryb serwera)", cx, top + 2, DIM, false);

		List<String> names = StatsTracker.profileNames();
		String active = StatsTracker.activeProfile();

		for (int r = 0; r < PROF_ROWS; r++) {
			int idx = builtStart + r;
			if (idx >= names.size()) break;
			String n = names.get(idx);
			boolean isActive = n.equals(active);
			int y = listY() + r * (PROF_ROW_H + PROF_ROW_GAP);
			boolean hover = inside(mouseX, mouseY, cx, y, cw, PROF_ROW_H);

			g.fill(cx, y, cx + cw, y + PROF_ROW_H, BASE);
			if (isActive) {
				g.fill(cx, y, cx + cw, y + PROF_ROW_H, alpha(PURPLE, 0x30));
				border(g, cx, y, cx + cw, y + PROF_ROW_H, alpha(PURPLE, 0xDD));
				g.fill(cx, y, cx + 4, y + PROF_ROW_H, PURPLE);
			} else {
				g.fill(cx, y, cx + cw, y + PROF_ROW_H, hover ? alpha(PURPLE, 0x1C) : ROW);
				border(g, cx, y, cx + cw, y + PROF_ROW_H, hover ? alpha(PURPLE, 0x90) : 0xFF1C1C3A);
			}

			g.text(this.font, n, cx + 12, y + (PROF_ROW_H - 8) / 2, isActive ? WHITE : (hover ? WHITE : DIM), false);
			if (isActive) {
				String tag = "AKTYWNY";
				g.text(this.font, tag, cx + cw - 10 - this.font.width(tag), y + (PROF_ROW_H - 8) / 2, GREEN, false);
			}
		}

		if (names.size() > PROF_ROWS) {
			String more = "kółko myszy - więcej profili (" + (builtStart + 1) + "-"
				+ Math.min(names.size(), builtStart + PROF_ROWS) + " z " + names.size() + ")";
			g.text(this.font, more, cx, listY() + PROF_ROWS * (PROF_ROW_H + PROF_ROW_GAP) + 1, alpha(DIM, 0xAA), false);
		}

		drawButton(g, mouseX, mouseY, cx, btnY(), half(), 22, "+ DODAJ PROFIL", PURPLE, addMode);
		drawButton(g, mouseX, mouseY, cx + half() + 8, btnY(), half(), 22,
			confirmDelete ? "NA PEWNO? KLIKNIJ" : "USUŃ PROFIL", RED, confirmDelete);

		if (addMode) {
			g.text(this.font, "Nazwa nowego profilu (np. Survival, Bedwars):", cx, addY(), DIM, false);

			int fy = fieldY();
			g.fill(cx, fy, cx + cw, fy + 20, BASE);
			g.fill(cx, fy, cx + cw, fy + 20, alpha(PURPLE, 0x14));
			border(g, cx, fy, cx + cw, fy + 20, alpha(PURPLE, 0xCC));

			String shown = nameText;
			if (shown.isEmpty()) {
				g.text(this.font, "wpisz nazwę...", cx + 6, fy + 6, alpha(DIM, 0x99), false);
			}
			boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
			g.text(this.font, shown + (blink ? "_" : ""), cx + 6, fy + 6, WHITE, false);

			drawButton(g, mouseX, mouseY, cx, saveY(), half(), 20, "ZAPISZ", GREEN, false);
			drawButton(g, mouseX, mouseY, cx + half() + 8, saveY(), half(), 20, "ANULUJ", DIM, false);
		}

		if (!msg.isEmpty()) {
			g.text(this.font, msg, cx, msgY(), msgColor, false);
		}
	}

	// ------------------------------------------------------------------ profil

	private void drawProfile(GuiGraphicsExtractor g, StatsData d, int cx, int top, int bottom, int cw) {
		int gap = 12;
		int cardW = (cw - gap) / 2;
		int cardH = Math.min(110, bottom - top);

		drawBigCard(g, cx, top, cardW, cardH, "ZGONY", String.valueOf(d.deaths), "razy zginąłeś", Tab.KILL.accent);
		drawBigCard(g, cx + cardW + gap, top, cardW, cardH, "ZABÓJSTWA", String.valueOf(d.kills),
			"zabitych graczy", GREEN);
	}

	private void drawBigCard(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String value,
							 String sub, int color) {
		g.fill(x - 2, y - 2, x + w + 2, y + h + 2, alpha(color, 0x14));
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, alpha(color, 0x20));
		for (int i = 0; i < h; i++) {
			g.fill(x, y + i, x + w, y + i + 1, lerp(alpha(color, 0x38), CARD, Math.min(1f, i / (h * 0.7f))));
		}
		border(g, x, y, x + w, y + h, alpha(color, 0xA0));
		g.fill(x, y, x + w, y + 3, color);
		g.fill(x, y + 3, x + w, y + 6, alpha(color, 0x30));

		g.text(this.font, label, x + 12, y + 14, WHITE, false);

		float s = 3.5f;
		int vw = bigWidth(value, s);
		int vx = x + (w - vw) / 2;
		int vy = y + h / 2 - 8;
		bigText(g, value, vx + 2, vy + 2, alpha(color, 0x55), s);
		bigText(g, value, vx, vy, color, s);

		g.text(this.font, sub, x + (w - this.font.width(sub)) / 2, y + h - 18, DIM, false);
	}

	// ------------------------------------------------------------------ ranking z głowami

	private void drawRanking(GuiGraphicsExtractor g, Map<String, Integer> map, String emptyText,
							 int cx, int top, int bottom, int cw, int color) {
		if (map.isEmpty()) {
			g.fill(cx, top, cx + cw, top + 48, CARD);
			border(g, cx, top, cx + cw, top + 48, alpha(color, 0x70));
			g.fill(cx, top, cx + 3, top + 48, color);
			g.text(this.font, emptyText, cx + 16, top + 20, DIM, false);
			return;
		}

		List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
		entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		int max = entries.get(0).getValue();

		int visible = Math.max(1, (bottom - top - 12) / (ROW_H + ROW_GAP));
		int maxScroll = Math.max(0, entries.size() - visible);
		if (scroll < 0) scroll = 0;
		if (scroll > maxScroll) scroll = maxScroll;

		int y = top;
		int start = (int) scroll;
		for (int i = start; i < entries.size() && i < start + visible; i++) {
			Map.Entry<String, Integer> e = entries.get(i);
			String name = e.getKey();
			int count = e.getValue();
			boolean first = i == 0;

			g.fill(cx, y, cx + cw, y + ROW_H, ROW);
			int barW = Math.max(3, (int) ((cw - 2) * (count / (double) max)));
			g.fill(cx + 1, y + 1, cx + barW, y + ROW_H - 1, alpha(color, 0x30));
			g.fill(cx + 1, y + ROW_H - 3, cx + barW, y + ROW_H - 1, alpha(color, 0xB0));
			border(g, cx, y, cx + cw, y + ROW_H, alpha(color, first ? 0x90 : 0x40));
			g.fill(cx, y, cx + 3, y + ROW_H, color);

			int hs = 20;
			int hx = cx + 10;
			int hy = y + (ROW_H - hs) / 2 - 1;
			border(g, hx - 2, hy - 2, hx + hs + 2, hy + hs + 2, alpha(color, 0xC0));
			if (!FaceUtil.drawFace(g, name, hx, hy, hs)) {
				drawFallbackAvatar(g, name, hx, hy, hs, color);
			}

			String rank = "#" + (i + 1);
			g.text(this.font, rank, hx + hs + 10, y + 5, first ? color : DIM, false);
			g.text(this.font, name, hx + hs + 10, y + 16 - 1, WHITE, false);

			String num = count + "x";
			float s = 1.6f;
			int nw = bigWidth(num, s);
			bigText(g, num, cx + cw - 12 - nw, y + (ROW_H - 13) / 2 - 1, color, s);
			y += ROW_H + ROW_GAP;
		}

		if (maxScroll > 0) {
			String more = "kółko myszy - przewijanie (" + (start + 1) + "-" + Math.min(entries.size(), start + visible)
				+ " z " + entries.size() + ")";
			g.text(this.font, more, cx, bottom - 6, alpha(DIM, 0xAA), false);
		}
	}

	/** Awatar zastępczy: kolorowy kwadrat z pierwszą literą nicku. */
	private void drawFallbackAvatar(GuiGraphicsExtractor g, String name, int x, int y, int size, int color) {
		g.fill(x, y, x + size, y + size, alpha(color, 0x55));
		g.fill(x + 2, y + 2, x + size - 2, y + size - 2, BASE);
		String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
		g.text(this.font, letter, x + (size - this.font.width(letter)) / 2, y + (size - 8) / 2, color, false);
	}
}
