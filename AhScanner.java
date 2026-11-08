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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passive reader: whenever an auction-house style container is open, reads the listings
 * (items whose lore has a "Price" line plus a seller / time line) and records unit prices.
 * It never clicks anything or sends any packets.
 */
public class AhScanner {
	private static final Pattern PRICE = Pattern.compile(
		"(?i)price\\s*[:\\-]?\\s*\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt])?");
	private static final Pattern SELLER = Pattern.compile(
		"(?i)(seller|sold by|owner|listed by|expires|time left|ends in)");

	private int tick;

	public void tick(Minecraft mc) {
		if (mc.player == null) return;
		if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
		if (++tick % 10 != 0) return;

		Map<String, List<Double>> prices = new HashMap<>();
		Map<String, String> names = new HashMap<>();

		for (Slot slot : cs.getMenu().slots) {
			if (slot.container == mc.player.getInventory()) continue;
			ItemStack st = slot.getItem();
			if (st.isEmpty()) continue;
			ItemLore lore = st.get(DataComponents.LORE);
			if (lore == null) continue;

			Double price = null;
			boolean listing = false;
			for (Component line : lore.lines()) {
				String t = line.getString();
				Matcher m = PRICE.matcher(t);
				if (price == null && m.find()) {
					try { price = Fmt.parse(m.group(1), m.group(2)); } catch (Exception ignored) {}
				}
				if (SELLER.matcher(t).find()) listing = true;
			}
			if (price == null || !listing || price <= 0) continue;

			String key = keyOf(st);
			if (key == null) continue;
			double unit = price / Math.max(1, st.getCount());
			prices.computeIfAbsent(key, k -> new ArrayList<>()).add(unit);
			names.putIfAbsent(key, st.getHoverName().getString());
		}

		if (prices.isEmpty()) return;
		long now = System.currentTimeMillis();
		for (Map.Entry<String, List<Double>> e : prices.entrySet()) {
			List<Double> l = e.getValue();
			Collections.sort(l);
			double floor = l.get(0);
			double med = l.get(l.size() / 2);
			SpangleStocksClient.store.record(e.getKey(), names.get(e.getKey()), floor, med, now);
		}
		SpangleStocksClient.store.saveIfDue(now);
	}

	/** Items with enchantments or damage aren't comparable, so they're skipped. */
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
