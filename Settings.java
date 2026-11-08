package dev.spangleddd.stocks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Settings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public double minPrice = 1;
	public double maxPrice = 1e10;
	/** AH sale fee as a fraction, used when estimating net profit. */
	public double fee = 0.05;
	public boolean alerts = true;
	public int tab = 0;

	public static Path dir() {
		Path p = FabricLoader.getInstance().getConfigDir().resolve("spangleddd_stocks");
		try { Files.createDirectories(p); } catch (Exception ignored) {}
		return p;
	}

	public static Settings load() {
		try {
			Path f = dir().resolve("settings.json");
			if (Files.exists(f)) {
				Settings s = GSON.fromJson(Files.readString(f), Settings.class);
				if (s != null) return s;
			}
		} catch (Exception ignored) {}
		return new Settings();
	}

	public void save() {
		try {
			Files.writeString(dir().resolve("settings.json"), GSON.toJson(this));
		} catch (Exception ignored) {}
	}
}
