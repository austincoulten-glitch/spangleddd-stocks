package dev.spangleddd.stocks;

import dev.spangleddd.stocks.PriceStore.Bucket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StocksScreen extends Screen {
	public static final String TITLE = "spangleddd stocks scratch version";

	// palette
	private static final int BG = 0xF0151821, PANEL = 0xFF1E222D, PANEL2 = 0xFF262B38, LINE = 0xFF343A4A;
	private static final int TEXT = 0xFFE5E7EB, MUTED = 0xFF8B93A5, GREEN = 0xFF4ADE80, RED = 0xFFF87171;
	private static final int BLUE = 0xFF60A5FA, GOLD = 0xFFFBBF24, ACCENT = 0xFF34D399, SEL = 0xFF2E3547;

	private static final double SL_LO = 1, SL_HI = 1e10;
	private static final int RH = 24;
	private static final String[] TABS = {"Short-term", "Long-term", "All items"};
	private static final DateTimeFormatter TIP = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter DAYF = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

	private int px, py, pw, ph;
	private int lx, ly, lw, lh, rx, rw, recH, gx, gy, gw, gh, slX, slY, slW;
	private List<Analyzer.Result> all = new ArrayList<>(), view = new ArrayList<>();
	private String selKey;
	private int scroll, drag; // drag: 0 none, 1 min handle, 2 max handle
	private int refreshTick;
	private Map<String, Item> itemMap;

	public StocksScreen() {
		super(Component.literal(TITLE));
	}

	@Override
	protected void init() {
		pw = Math.min(width - 16, 620);
		ph = Math.min(height - 16, 350);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		lx = px + 8; ly = py + 46; lw = 200; lh = ph - 46 - 54;
		rx = px + 216; rw = pw - 224;
		recH = 68;
		gx = rx; gy = py + 68; gw = rw; gh = Math.max(60, (ph - 54) - 68 - recH - 6);
		slX = px + 14; slW = pw - 28; slY = py + ph - 22;
		refresh();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		if (++refreshTick % 40 == 0) refresh();
	}

	// ------------------------------------------------------------------ data

	private Settings cfg() { return SpangleStocksClient.settings; }

	private void refresh() {
		long now = System.currentTimeMillis();
		all = Analyzer.analyzeAll(SpangleStocksClient.store, now, cfg().fee);
		view = new ArrayList<>();
		for (Analyzer.Result r : all) {
			if (r.cur < cfg().minPrice || r.cur > cfg().maxPrice) continue;
			if (cfg().tab == 0 && r.shortSig == null) continue;
			if (cfg().tab == 1 && r.longSig == null) continue;
			view.add(r);
		}
		if (cfg().tab == 0) view.sort(Comparator.comparingDouble((Analyzer.Result r) -> -r.shortSig.score));
		else if (cfg().tab == 1) view.sort(Comparator.comparingDouble((Analyzer.Result r) -> -r.longSig.score));
		else view.sort(Comparator.comparing((Analyzer.Result r) -> !r.ready).thenComparing(r -> r.name));

		boolean found = false;
		for (Analyzer.Result r : view) if (r.key.equals(selKey)) found = true;
		if (!found) selKey = view.isEmpty() ? null : view.get(0).key;
		int maxScroll = Math.max(0, view.size() - Math.max(1, lh / RH));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
	}

	private Analyzer.Result selected() {
		for (Analyzer.Result r : view) if (r.key.equals(selKey)) return r;
		return null;
	}

	private ItemStack iconFor(String key) {
		if (itemMap == null) {
			itemMap = new HashMap<>();
			for (Item it : BuiltInRegistries.ITEM) itemMap.put(BuiltInRegistries.ITEM.getKey(it).getPath(), it);
		}
		int bar = key.indexOf('|');
		Item it = itemMap.get(bar >= 0 ? key.substring(0, bar) : key);
		return it == null ? ItemStack.EMPTY : new ItemStack(it);
	}

	// ------------------------------------------------------------------ helpers

	private void rect(GuiGraphics g, int x, int y, int w, int h, int c) {
		g.fill(x, y, x + w, y + h, c);
	}

	private void box(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
		rect(g, x, y, w, h, border);
		rect(g, x + 1, y + 1, w - 2, h - 2, fill);
	}

	private void text(GuiGraphics g, String s, int x, int y, int c) {
		g.drawString(font, s, x, y, c);
	}

	private void textR(GuiGraphics g, String s, int xRight, int y, int c) {
		g.drawString(font, s, xRight - font.width(s), y, c);
	}

	private String fit(String s, int maxW) {
		if (font.width(s) <= maxW) return s;
		while (s.length() > 1 && font.width(s + "..") > maxW) s = s.substring(0, s.length() - 1);
		return s + "..";
	}

	private boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, int mx, int my) {
		boolean hover = in(mx, my, x, y, w, h);
		int fill = active ? 0xFF1F5F4A : (hover ? 0xFF303648 : PANEL2);
		box(g, x, y, w, h, fill, active ? ACCENT : LINE);
		g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, active ? 0xFFFFFFFF : TEXT);
	}

	private double sliderValue(double t) {
		double v = Math.pow(10, t * Math.log10(SL_HI / SL_LO)) * SL_LO;
		double mag = Math.pow(10, Math.floor(Math.log10(v)) - 1);
		return Math.max(SL_LO, Math.min(SL_HI, Math.round(v / mag) * mag));
	}

	private double sliderT(double v) {
		return Math.log10(Math.max(SL_LO, v) / SL_LO) / Math.log10(SL_HI / SL_LO);
	}

	// ------------------------------------------------------------------ render

	@Override
	public void render(GuiGraphics g, int mx, int my, float delta) {
		rect(g, 0, 0, width, height, 0x99000000);
		box(g, px, py, pw, ph, BG, LINE);

		// header
		rect(g, px + 1, py + 1, pw - 2, 21, PANEL);
		rect(g, px + 8, py + 6, 3, 10, ACCENT);
		text(g, TITLE, px + 16, py + 7, TEXT);
		button(g, px + pw - 24, py + 4, 16, 14, "x", false, mx, my);

		// tabs + controls
		for (int i = 0; i < TABS.length; i++) {
			button(g, px + 8 + i * 72, py + 26, 68, 16, TABS[i], cfg().tab == i, mx, my);
		}
		int bx = px + pw - 8;
		button(g, bx - 62, py + 26, 62, 16, cfg().alerts ? "Alerts: ON" : "Alerts: OFF", cfg().alerts, mx, my);
		String feeS = "Fee " + Math.round(cfg().fee * 100) + "%";
		button(g, bx - 62 - 4 - 14, py + 26, 14, 16, "+", false, mx, my);
		textR(g, feeS, bx - 62 - 4 - 14 - 4, py + 30, MUTED);
		int feeW = font.width(feeS);
		button(g, bx - 62 - 4 - 14 - 4 - feeW - 4 - 14, py + 26, 14, 16, "-", false, mx, my);

		renderList(g, mx, my);
		renderDetail(g, mx, my);
		renderSliders(g, mx, my);
	}

	private void renderList(GuiGraphics g, int mx, int my) {
		box(g, lx, ly, lw, lh, PANEL, LINE);
		if (view.isEmpty()) {
			String a = SpangleStocksClient.store.items.isEmpty() ? "No prices recorded yet." : "Nothing matches.";
			String b = SpangleStocksClient.store.items.isEmpty() ? "Open /ah and browse - prices" : "Try another tab or widen";
			String c = SpangleStocksClient.store.items.isEmpty() ? "are saved automatically." : "the price range below.";
			g.drawCenteredString(font, a, lx + lw / 2, ly + 20, TEXT);
			g.drawCenteredString(font, b, lx + lw / 2, ly + 34, MUTED);
			g.drawCenteredString(font, c, lx + lw / 2, ly + 44, MUTED);
			return;
		}
		int rows = Math.max(1, lh / RH);
		for (int i = 0; i < rows; i++) {
			int idx = scroll + i;
			if (idx >= view.size()) break;
			Analyzer.Result r = view.get(idx);
			int y = ly + i * RH;
			boolean sel = r.key.equals(selKey);
			if (sel) rect(g, lx + 1, y, lw - 2, RH, SEL);
			else if (in(mx, my, lx, y, lw, RH)) rect(g, lx + 1, y, lw - 2, RH, 0xFF252A38);
			if (sel) rect(g, lx + 1, y, 2, RH, ACCENT);

			ItemStack ic = iconFor(r.key);
			if (!ic.isEmpty()) g.renderItem(ic, lx + 6, y + 4);
			text(g, fit(r.name, lw - 100), lx + 26, y + 4, TEXT);
			text(g, r.ready ? Fmt.money(r.cur) : "collecting data...", lx + 26, y + 14, MUTED);

			int right = lx + lw - 8;
			if (r.strong()) {
				rect(g, lx + lw - 12, y + 3, 4, 4, GOLD);
				right -= 8;
			}
			if (cfg().tab == 0 && r.shortSig != null) {
				textR(g, "+" + Math.round(r.shortSig.roiHigh) + "%", right, y + 8, GREEN);
			} else if (cfg().tab == 1 && r.longSig != null) {
				textR(g, "+" + Math.round(r.longSig.roiHigh) + "%", right, y + 8, BLUE);
			} else if (cfg().tab == 2) {
				int xx = right;
				if (r.longSig != null) { textR(g, "L", xx, y + 8, BLUE); xx -= 10; }
				if (r.shortSig != null) { textR(g, "S", xx, y + 8, GREEN); }
			}
			rect(g, lx + 4, y + RH - 1, lw - 8, 1, 0xFF262B38);
		}
		// scrollbar
		if (view.size() > rows) {
			int bh = Math.max(14, lh * rows / view.size());
			int by = ly + (lh - bh) * scroll / Math.max(1, view.size() - rows);
			rect(g, lx + lw - 3, by, 2, bh, 0xFF4B5367);
		}
	}

	private void renderDetail(GuiGraphics g, int mx, int my) {
		Analyzer.Result r = selected();
		if (r == null) {
			box(g, rx, ly, rw, ph - 46 - 54, PANEL, LINE);
			g.drawCenteredString(font, "Select an item to see its graph", rx + rw / 2, ly + 50, MUTED);
			return;
		}
		// header line
		ItemStack ic = iconFor(r.key);
		if (!ic.isEmpty()) g.renderItem(ic, rx, py + 45);
		text(g, fit(r.name, rw - 150), rx + 20, py + 47, TEXT);
		if (r.ready && r.med7 > 0) {
			double chg = (r.cur / r.med7 - 1) * 100;
			String p = Fmt.money(r.cur) + "  " + Fmt.pct(chg);
			textR(g, p, rx + rw, py + 47, chg >= 0 ? GREEN : RED);
		} else {
			textR(g, Fmt.money(r.cur), rx + rw, py + 47, TEXT);
		}
		String stats = r.ready
			? String.format(Locale.ROOT, "7d low %s  med %s  high %s  vol %.0f%%  trend %s/day",
				Fmt.money(r.low7), Fmt.money(r.med7), Fmt.money(r.high7), r.volPct, Fmt.pct(r.slopePctDay))
			: "Need ~12h of history (" + r.samples + " samples so far)";
		text(g, fit(stats, rw), rx, py + 58, MUTED);

		renderGraph(g, r, mx, my);

		// recommendation cards
		int cy = gy + gh + 4;
		int cw = (rw - 6) / 2;
		card(g, rx, cy, cw, recH, "SHORT-TERM", GREEN, r.shortSig, r);
		card(g, rx + cw + 6, cy, cw, recH, "LONG-TERM", BLUE, r.longSig, r);
	}

	private void card(GuiGraphics g, int x, int y, int w, int h, String title, int color, Analyzer.Signal s, Analyzer.Result r) {
		box(g, x, y, w, h, PANEL, s != null ? color : LINE);
		rect(g, x + 1, y + 1, w - 2, 12, PANEL2);
		text(g, title, x + 5, y + 3, color);
		if (s == null) {
			String why = r.stale ? "Prices are stale - browse /ah" : (!r.ready ? "Not enough data yet" : "No buy signal right now");
			text(g, fit(why, w - 10), x + 5, y + 20, MUTED);
			return;
		}
		boolean strong = s.score >= (title.startsWith("SHORT") ? Analyzer.SHORT_ALERT : Analyzer.LONG_ALERT);
		if (strong) textR(g, "BUY NOW", x + w - 5, y + 3, GOLD);
		text(g, fit("Buy <= " + Fmt.money(s.buy) + "  Sell " + Fmt.money(s.sellLow) + "-" + Fmt.money(s.sellHigh), w - 10), x + 5, y + 17, TEXT);
		text(g, fit("Profit " + Fmt.money(s.profitLow) + " to " + Fmt.money(s.profitHigh) + " /unit", w - 10), x + 5, y + 28, GREEN);
		text(g, fit(String.format(Locale.ROOT, "ROI %.0f%% - %.0f%%  (%s)", s.roiLow, s.roiHigh, s.horizon), w - 10), x + 5, y + 39, MUTED);
		text(g, fit(s.why, w - 10), x + 5, y + 50, MUTED);
	}

	private void renderGraph(GuiGraphics g, Analyzer.Result r, int mx, int my) {
		box(g, gx, gy, gw, gh, PANEL, LINE);
		text(g, TITLE, gx + 6, gy + 4, ACCENT);
		int lgx = gx + gw - 6;
		textR(g, "Last week", lgx, gy + 4, MUTED);
		rect(g, lgx - font.width("Last week") - 10, gy + 6, 6, 4, 0xFF6B7280);
		int lg2 = lgx - font.width("Last week") - 16;
		textR(g, "This week", lg2, gy + 4, TEXT);
		rect(g, lg2 - font.width("This week") - 10, gy + 6, 6, 4, ACCENT);

		int plx = gx + 46, ply = gy + 18, plw = gw - 54, plh = gh - 18 - 14;
		if (plw < 20 || plh < 20) return;

		long now = System.currentTimeMillis();
		long t0 = now - 7 * Analyzer.DAY;
		double lo = Double.MAX_VALUE, hi = 0;
		for (Bucket b : r.week) { lo = Math.min(lo, b.floor); hi = Math.max(hi, b.floor); }
		for (Bucket b : r.prev) { lo = Math.min(lo, b.floor); hi = Math.max(hi, b.floor); }
		if (hi <= 0 || lo == Double.MAX_VALUE) {
			g.drawCenteredString(font, "No data yet for this item", gx + gw / 2, gy + gh / 2, MUTED);
			return;
		}
		double pad = Math.max((hi - lo) * 0.12, hi * 0.01);
		lo = Math.max(0, lo - pad);
		hi = hi + pad;

		// grid + y labels
		for (int i = 0; i <= 4; i++) {
			int y = ply + plh - plh * i / 4;
			rect(g, plx, y, plw, 1, 0xFF2A3040);
			textR(g, Fmt.money(lo + (hi - lo) * i / 4), plx - 4, y - 4, MUTED);
		}
		// x labels (day boundaries)
		ZoneId zone = ZoneId.systemDefault();
		for (int d = 0; d <= 7; d++) {
			long t = t0 + d * Analyzer.DAY;
			int x = plx + (int) (plw * d / 7.0);
			rect(g, x, ply, 1, plh, 0xFF232837);
			if (d < 7) {
				String lab = DAYF.format(Instant.ofEpochMilli(t + Analyzer.DAY / 2).atZone(zone));
				g.drawString(font, lab, x + (plw / 7 - font.width(lab)) / 2, ply + plh + 3, MUTED);
			}
		}
		// median line
		if (r.ready) {
			int my0 = (int) (ply + plh - (r.med7 - lo) / (hi - lo) * plh);
			for (int x = plx; x < plx + plw; x += 6) rect(g, x, my0, 3, 1, 0x66FBBF24);
		}

		drawSeries(g, r.prev, 7 * Analyzer.DAY, t0, plx, ply, plw, plh, lo, hi, 0xFF6B7280);
		drawSeries(g, r.week, 0, t0, plx, ply, plw, plh, lo, hi, ACCENT);

		// hover tooltip
		if (in(mx, my, plx, ply, plw, plh) && !r.week.isEmpty()) {
			double frac = (mx - plx) / (double) plw;
			Bucket best = null;
			double bd = Double.MAX_VALUE;
			for (Bucket b : r.week) {
				double d = Math.abs((b.t - t0) / (7.0 * Analyzer.DAY) - frac);
				if (d < bd) { bd = d; best = b; }
			}
			Bucket pbest = null;
			double pd = Double.MAX_VALUE;
			for (Bucket b : r.prev) {
				double d = Math.abs((b.t + 7 * Analyzer.DAY - t0) / (7.0 * Analyzer.DAY) - frac);
				if (d < pd) { pd = d; pbest = b; }
			}
			if (best != null) {
				int bx = plx + (int) ((best.t - t0) / (7.0 * Analyzer.DAY) * plw);
				int by = (int) (ply + plh - (best.floor - lo) / (hi - lo) * plh);
				rect(g, bx, ply, 1, plh, 0x66FFFFFF);
				rect(g, bx - 2, by - 2, 5, 5, 0xFFFFFFFF);
				String l1 = TIP.format(Instant.ofEpochMilli(best.t).atZone(zone)) + "  " + Fmt.money(best.floor);
				String l2 = (pbest != null && pd < 0.02) ? "Last week " + Fmt.money(pbest.floor) : null;
				int tw = Math.max(font.width(l1), l2 == null ? 0 : font.width(l2)) + 8;
				int th = l2 == null ? 14 : 24;
				int tx = Math.min(bx + 6, gx + gw - tw - 2);
				int ty = Math.max(ply, Math.min(by - th - 4, ply + plh - th));
				box(g, tx, ty, tw, th, 0xFF0F1219, ACCENT);
				text(g, l1, tx + 4, ty + 3, TEXT);
				if (l2 != null) text(g, l2, tx + 4, ty + 13, MUTED);
			}
		}
	}

	private void drawSeries(GuiGraphics g, List<Bucket> pts, long shift, long t0, int plx, int ply, int plw, int plh,
							double lo, double hi, int color) {
		int px0 = 0, py0 = 0;
		long lastT = 0;
		boolean has = false;
		for (Bucket b : pts) {
			int x = plx + (int) ((b.t + shift - t0) / (7.0 * Analyzer.DAY) * plw);
			int y = (int) (ply + plh - (b.floor - lo) / (hi - lo) * plh);
			x = Math.max(plx, Math.min(plx + plw - 1, x));
			if (has && b.t - lastT <= 12 * 3600 * 1000L && x > px0) {
				for (int xx = px0; xx < x; xx++) {
					double f0 = (xx - px0) / (double) (x - px0);
					double f1 = (xx + 1 - px0) / (double) (x - px0);
					int ya = (int) (py0 + (y - py0) * f0);
					int yb = (int) (py0 + (y - py0) * f1);
					rect(g, xx, Math.min(ya, yb), 1, Math.abs(yb - ya) + 2, color);
				}
			}
			rect(g, x - 1, y - 1, 3, 3, color);
			px0 = x; py0 = y; lastT = b.t; has = true;
		}
	}

	private void renderSliders(GuiGraphics g, int mx, int my) {
		int ty = py + ph - 46;
		text(g, "Price range (per unit)", px + 14, ty, MUTED);
		g.drawCenteredString(font, Fmt.money(cfg().minPrice) + "  -  " + Fmt.money(cfg().maxPrice), px + pw / 2, ty, TEXT);
		textR(g, view.size() + " items shown", px + pw - 14, ty, MUTED);

		int trackY = slY - 8;
		rect(g, slX, trackY + 4, slW, 4, 0xFF2A3040);
		int a = slX + (int) (sliderT(cfg().minPrice) * slW);
		int b = slX + (int) (sliderT(cfg().maxPrice) * slW);
		rect(g, a, trackY + 4, Math.max(1, b - a), 4, ACCENT);
		handle(g, a, trackY, drag == 1 || in(mx, my, a - 5, trackY - 2, 10, 16), "MIN");
		handle(g, b, trackY, drag == 2 || in(mx, my, b - 5, trackY - 2, 10, 16), "MAX");
		text(g, "$1", slX - 6, slY + 6, MUTED);
		textR(g, "$10B", slX + slW + 6, slY + 6, MUTED);
	}

	private void handle(GuiGraphics g, int cx, int y, boolean hot, String label) {
		box(g, cx - 4, y, 8, 12, hot ? 0xFFFFFFFF : 0xFFD1D5DB, hot ? ACCENT : LINE);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		double mx = e.x(), my = e.y();
		if (e.button() != 0) return super.mouseClicked(e, dbl);

		if (in(mx, my, px + pw - 24, py + 4, 16, 14)) { onClose(); return true; }
		for (int i = 0; i < TABS.length; i++) {
			if (in(mx, my, px + 8 + i * 72, py + 26, 68, 16)) {
				cfg().tab = i; scroll = 0; refresh(); cfg().save(); return true;
			}
		}
		int bx = px + pw - 8;
		if (in(mx, my, bx - 62, py + 26, 62, 16)) { cfg().alerts = !cfg().alerts; cfg().save(); return true; }
		String feeS = "Fee " + Math.round(cfg().fee * 100) + "%";
		int feeW = font.width(feeS);
		if (in(mx, my, bx - 62 - 4 - 14, py + 26, 14, 16)) { cfg().fee = Math.min(0.5, cfg().fee + 0.01); refresh(); cfg().save(); return true; }
		if (in(mx, my, bx - 62 - 4 - 14 - 4 - feeW - 4 - 14, py + 26, 14, 16)) { cfg().fee = Math.max(0, cfg().fee - 0.01); refresh(); cfg().save(); return true; }

		// list
		if (in(mx, my, lx, ly, lw, lh)) {
			int idx = scroll + (int) ((my - ly) / RH);
			if (idx >= 0 && idx < view.size()) selKey = view.get(idx).key;
			return true;
		}
		// sliders
		int trackY = slY - 8;
		if (in(mx, my, slX - 8, trackY - 4, slW + 16, 22)) {
			double t = Math.max(0, Math.min(1, (mx - slX) / slW));
			double a = sliderT(cfg().minPrice), b = sliderT(cfg().maxPrice);
			drag = Math.abs(t - a) <= Math.abs(t - b) ? 1 : 2;
			applySlider(t);
			return true;
		}
		return super.mouseClicked(e, dbl);
	}

	private void applySlider(double t) {
		double v = sliderValue(Math.max(0, Math.min(1, t)));
		if (drag == 1) cfg().minPrice = Math.min(v, cfg().maxPrice);
		else if (drag == 2) cfg().maxPrice = Math.max(v, cfg().minPrice);
		refresh();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (drag != 0) {
			applySlider((e.x() - slX) / slW);
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (drag != 0) {
			drag = 0;
			cfg().save();
			return true;
		}
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (in(mx, my, lx, ly, lw, lh)) {
			int rows = Math.max(1, lh / RH);
			int max = Math.max(0, view.size() - rows);
			scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy) * 2));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}
}
