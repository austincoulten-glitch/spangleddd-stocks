package dev.spangleddd.stocks;

import java.util.Locale;

public final class Fmt {
	private Fmt() {}

	public static String money(double v) {
		double a = Math.abs(v);
		String s;
		if (a >= 1e12) s = String.format(Locale.ROOT, "%.2fT", a / 1e12);
		else if (a >= 1e9) s = String.format(Locale.ROOT, "%.2fB", a / 1e9);
		else if (a >= 1e6) s = String.format(Locale.ROOT, "%.2fM", a / 1e6);
		else if (a >= 1e3) s = String.format(Locale.ROOT, "%.1fK", a / 1e3);
		else s = String.format(Locale.ROOT, "%.0f", a);
		return (v < 0 ? "-$" : "$") + s;
	}

	public static String pct(double p) {
		return String.format(Locale.ROOT, "%+.1f%%", p);
	}

	/** Parses "1,250.5" with optional k/m/b/t suffix. */
	public static double parse(String num, String suffix) {
		double v = Double.parseDouble(num.replace(",", ""));
		if (suffix != null && !suffix.isEmpty()) {
			switch (Character.toLowerCase(suffix.charAt(0))) {
				case 'k' -> v *= 1e3;
				case 'm' -> v *= 1e6;
				case 'b' -> v *= 1e9;
				case 't' -> v *= 1e12;
				default -> {}
			}
		}
		return v;
	}
}
