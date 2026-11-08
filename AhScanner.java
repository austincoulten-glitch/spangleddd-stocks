package dev.spangleddd.stocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AhScanner {
	private static final Pattern LABELED = Pattern.compile(
		"(?i)price\\D{0,6}([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt])?\\b");
	private static final Pattern DOLLAR = Pattern.compile(
		"\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbtKMBT])?");

	private int tick;
	private long lastDebug;

	public void tick(Minecraft mc) {
		if (mc.player == null) return;
		if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
		if (++tick % 10 != 0) return;

		Map<String, List<Double>> prices = new HashMap<>();
		Map<String, String> names = new HashMap<>();
		StringBuilder dbg = new StringBuilder();
		int dumped = 0;

		for (Slot slot : cs.getMenu().slots) {
			if (slot.container == mc.player.getInventory()) continue;
			ItemStack st = slot.getItem();
			if (st.isEmpty()) continue;
			ItemLore lore = st.get(DataComponents.LORE);
			if (lore == null) continue;

			if (dumped < 4) {
				dbg.append("ITEM: ").append(st.getHoverName().getString())
					.append(" x").append(st.getCount()).append('\n');
				for (Component line : lore.lines()) dbg.append("   | ").append(line.getString()).append('\n');
				dumped++;
			}

			Double price = null;
			for (Component line : lore.lines()) {
				String t = line.getString();
				Matcher m = LABELED.matcher(t);
				if (m.find()) {
					try { price = Fmt.parse(m.group(1), m.group(2)); } catch (Exception ignored) {}
					if (price != null) break;
				}
			}
			if (price == null) {
				for (Component line : lore.lines()) {
					Matcher m = DOLLAR.matcher(line.getString());
					if (m.find()) {
						try { price = Fmt.parse(m.group(1), m.group(2)); } catch (Exception ignored) {}
						if (price != null) break;
					}
				}
			}
			if (price == null || price <= 0) continue;

			String key = keyOf(st);
			if (key == null) continue;
			double unit = price / Math.max(1, st.getCount());
			prices.computeIfAbsent(key, k -> new ArrayList<>()).add(unit);
			names.putIfAbsent(key, st.getHoverName().getString());
		}

		long now = System.currentTimeMillis();
		if (dumped > 0 && now - lastDebug > 3000) {
			lastDebug = now;
			try {
				Files.writeString(Settings.dir().resolve("debug.txt"),
					"Matched items this scan: " + prices.size() + "\n\n" + dbg);
			} catch (Exception ignored) {}
		}

		if (prices.isEmpty()) return;
		for (Map.Entry<String, List<Double>> e : prices.entrySet()) {
			List<Double> l = e.getValue();
			Collections.sort(l);
			SpangleStocksClient.store.record(e.getKey(), names.get(e.getKey()), l.get(0), l.get(l.size() / 2), now);
		}
		SpangleStocksClient.store.saveIfDue(now);
	}

	public static String keyOf(ItemStack st) {
		if (st.isDamaged()) return null;
		ItemEnchantments e = st.get(DataComponents.ENCHANTMENTS);
		if (e != null && !e.isEmpty()) return null;
		e = st.get(DataComponents.STORED_ENCHANTMENTS);
		if (e != null && !e.isEmpty()) return null;
		var id = BuiltInRegistries.ITEM.getKey(st.getItem());
		String k = id.getPath();
		Component cn = st.get(DataComponents.CUSTOM_NAME);
		if (cn != null) k += "|" + cn.getString();
		return k;
	}
}
