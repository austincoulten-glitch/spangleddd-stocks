package dev.spangleddd.stocks;

import dev.spangleddd.stocks.PriceStore.Bucket;
import dev.spangleddd.stocks.PriceStore.ItemData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class Analyzer {
	private Analyzer() {}

	public static final long DAY = 24L * 3600 * 1000;
	public static final double SHORT_ALERT = 14;
	public static final double LONG_ALERT = 20;

	public static class Signal {
		public double score, buy, sellLow, sellHigh, profitLow, profitHigh, roiLow, roiHigh;
		public String why = "", horizon = "";
	}

	public static class Result {
		public String key, name;
		public boolean ready, stale;
		public double cur, med7, low7, high7, volPct, slopePctDay, wowPct = Double.NaN;
		public int samples;
		public long lastSeen;
		public Signal shortSig, longSig;
		public List<Bucket> week = new ArrayList<>(), prev = new ArrayList<>();

		public boolean strong() {
			return (shortSig != null && shortSig.score >= SHORT_ALERT)
				|| (longSig != null && longSig.score >= LONG_ALERT);
		}
	}

	public static List<Result> analyzeAll(PriceStore store, long now, double fee) {
		List<Result> out = new ArrayList<>();
		synchronized (store) {
			for (Map.Entry<String, ItemData> e : store.items.entrySet()) {
				Result r = analyze(e.getKey(), e.getValue(), now, fee);
				if (r != null) out.add(r);
			}
		}
		return out;
	}

	public static Result analyze(String key, ItemData d, long now, double fee) {
		if (d.b.isEmpty()) return null;
		Result r = new Result();
		r.key = key;
		r.name = d.name != null ? d.name : key;
		for (Bucket b : d.b) {
			if (b.t >= now - 7 * DAY) r.week.add(copy(b));
			else if (b.t >= now - 14 * DAY) r.prev.add(copy(b));
		}
		Bucket last = d.b.get(d.b.size() - 1);
		r.cur = last.floor;
		r.lastSeen = last.t;
		r.samples = r.week.size();
		r.stale = now - last.t > 12 * 3600 * 1000L;

		int n = r.week.size();
		if (n < 6 || r.week.get(n - 1).t - r.week.get(0).t < 12 * 3600 * 1000L) return r;
		r.ready = true;

		double[] f = new double[n];
		double sum = 0, lo = Double.MAX_VALUE, hi = 0;
		for (int i = 0; i < n; i++) {
			f[i] = r.week.get(i).floor;
			sum += f[i];
			lo = Math.min(lo, f[i]);
			hi = Math.max(hi, f[i]);
		}
		double mean = sum / n;
		double[] sorted = f.clone();
		Arrays.sort(sorted);
		double med = n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
		double var = 0;
		for (double v : f) var += (v - mean) * (v - mean);
		double sd = Math.sqrt(var / n);

		// least squares slope, x in days
		double t0 = r.week.get(0).t;
		double sx = 0, sy = 0, sxx = 0, sxy = 0;
		for (int i = 0; i < n; i++) {
			double x = (r.week.get(i).t - t0) / (double) DAY;
			sx += x; sy += f[i]; sxx += x * x; sxy += x * f[i];
		}
		double den = n * sxx - sx * sx;
		double slope = den == 0 ? 0 : (n * sxy - sx * sy) / den;

		r.med7 = med;
		r.low7 = lo;
		r.high7 = hi;
		r.volPct = mean == 0 ? 0 : sd / mean * 100;
		r.slopePctDay = mean == 0 ? 0 : slope / mean * 100;
		if (r.prev.size() >= 4) {
			double ps = 0;
			for (Bucket b : r.prev) ps += b.floor;
			double pm = ps / r.prev.size();
			if (pm > 0) r.wowPct = (mean / pm - 1) * 100;
		}

		if (r.stale) return r; // prices too old to act on

		// ---- Short-term: buy a dip, sell back toward the weekly median
		double dip = (med - r.cur) / med;
		if (dip >= 0.07 && r.volPct >= 2.5 && r.slopePctDay > -5) {
			Signal s = new Signal();
			s.buy = r.cur;
			s.sellHigh = med;
			s.sellLow = r.cur + (med - r.cur) * 0.5;
			finish(s, fee);
			if (s.profitHigh > 0 && s.roiHigh >= 3) {
				double z = sd > 0 ? Math.min(3, (med - r.cur) / sd) : 0;
				s.score = dip * 100 + z * 4 + Math.min(n, 24) / 24.0 * 3;
				s.horizon = "hours to ~2 days";
				s.why = String.format("%.0f%% below 7d median", dip * 100);
				r.shortSig = s;
			}
		}

		// ---- Long-term: steady climb with moderate volatility
		boolean up = r.slopePctDay >= 1.0 && (Double.isNaN(r.wowPct) || r.wowPct >= 3);
		if (up && r.volPct <= 35 && r.cur <= med * 1.2 && n >= 12) {
			Signal s = new Signal();
			double gain = Math.min(r.slopePctDay * 14, 100) / 100.0;
			s.buy = r.cur;
			s.sellHigh = r.cur * (1 + gain);
			s.sellLow = r.cur * (1 + gain * 0.4);
			finish(s, fee);
			if (s.profitHigh > 0) {
				s.score = r.slopePctDay * 8 + (Double.isNaN(r.wowPct) ? 0 : r.wowPct * 0.5);
				s.horizon = "1-2 weeks";
				s.why = String.format("+%.1f%%/day trend%s", r.slopePctDay,
					Double.isNaN(r.wowPct) ? "" : String.format(", %s wk/wk", Fmt.pct(r.wowPct)));
				r.longSig = s;
			}
		}
		return r;
	}

	private static void finish(Signal s, double fee) {
		s.profitLow = s.sellLow * (1 - fee) - s.buy;
		s.profitHigh = s.sellHigh * (1 - fee) - s.buy;
		s.roiLow = s.profitLow / s.buy * 100;
		s.roiHigh = s.profitHigh / s.buy * 100;
	}

	private static Bucket copy(Bucket b) {
		Bucket c = new Bucket();
		c.t = b.t; c.floor = b.floor; c.med = b.med; c.n = b.n;
		return c;
	}
}
