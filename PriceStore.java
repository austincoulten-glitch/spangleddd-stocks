package dev.spangleddd.stocks;

import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stores 30-minute price buckets per item for the current week and the week before. */
public class PriceStore {
	public static final long BUCKET = 30L * 60 * 1000;
	public static final long KEEP = 15L * 24 * 3600 * 1000;
	private static final Gson GSON = new Gson();

	public static class Bucket {
		public long t;
		public double floor; // lowest unit price seen in the bucket
		public double med;   // average of the median unit price seen
		public int n;
	}

	public static class ItemData {
		public String name;
		public List<Bucket> b = new ArrayList<>();
	}

	public Map<String, ItemData> items = new LinkedHashMap<>();
	private transient boolean dirty;
	private transient long lastSave;

	private static Path file() {
		return Settings.dir().resolve("prices.json");
	}

	public static PriceStore load() {
		try {
			Path f = file();
			if (Files.exists(f)) {
				PriceStore s = GSON.fromJson(Files.readString(f), PriceStore.class);
				if (s != null && s.items != null) {
					s.prune(System.currentTimeMillis());
					return s;
				}
			}
		} catch (Exception ignored) {}
		return new PriceStore();
	}

	public synchronized void record(String key, String name, double floor, double median, long now) {
		ItemData d = items.computeIfAbsent(key, k -> new ItemData());
		d.name = name;
		long t = now / BUCKET * BUCKET;
		Bucket last = d.b.isEmpty() ? null : d.b.get(d.b.size() - 1);
		if (last != null && last.t == t) {
			last.floor = Math.min(last.floor, floor);
			last.med = (last.med * last.n + median) / (last.n + 1);
			last.n++;
		} else {
			Bucket nb = new Bucket();
			nb.t = t;
			nb.floor = floor;
			nb.med = median;
			nb.n = 1;
			d.b.add(nb);
		}
		dirty = true;
	}

	public synchronized void prune(long now) {
		Iterator<Map.Entry<String, ItemData>> it = items.entrySet().iterator();
		while (it.hasNext()) {
			ItemData d = it.next().getValue();
			d.b.removeIf(b -> b.t < now - KEEP);
			if (d.b.isEmpty()) it.remove();
		}
	}

	public synchronized void saveIfDue(long now) {
		if (dirty && now - lastSave > 60_000) save();
	}

	public synchronized void save() {
		try {
			prune(System.currentTimeMillis());
			Path f = file();
			Path tmp = f.resolveSibling("prices.json.tmp");
			Files.writeString(tmp, GSON.toJson(this));
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
			lastSave = System.currentTimeMillis();
		} catch (Exception ignored) {}
	}
}
