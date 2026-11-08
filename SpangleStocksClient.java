package dev.spangleddd.stocks;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Map;

public class SpangleStocksClient implements ClientModInitializer {
	public static PriceStore store;
	public static Settings settings;

	private static final AhScanner scanner = new AhScanner();
	private static final Map<String, Long> alerted = new HashMap<>();
	private static boolean openNext;
	private static long nextAlertCheck;

	@Override
	public void onInitializeClient() {
		store = PriceStore.load();
		settings = Settings.load();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (openNext) {
				openNext = false;
				mc.setScreen(new StocksScreen());
			}
			scanner.tick(mc);
			checkAlerts(mc);
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			for (String name : new String[] {"spangle", "stocks"}) {
				dispatcher.register(ClientCommandManager.literal(name).executes(ctx -> {
					openNext = true; // open next tick so the chat screen doesn't close it
					return Command.SINGLE_SUCCESS;
				}));
			}
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			store.save();
			settings.save();
		});
	}

	private static void checkAlerts(Minecraft mc) {
		if (mc.player == null || !settings.alerts) return;
		long now = System.currentTimeMillis();
		if (now < nextAlertCheck) return;
		nextAlertCheck = now + 30_000;

		for (Analyzer.Result r : Analyzer.analyzeAll(store, now, settings.fee)) {
			if (r.cur < settings.minPrice || r.cur > settings.maxPrice) continue;
			Analyzer.Signal s = null;
			String tag = null;
			if (r.shortSig != null && r.shortSig.score >= Analyzer.SHORT_ALERT) { s = r.shortSig; tag = "SHORT"; }
			else if (r.longSig != null && r.longSig.score >= Analyzer.LONG_ALERT) { s = r.longSig; tag = "LONG"; }
			if (s == null) continue;

			String k = r.key + tag;
			Long last = alerted.get(k);
			if (last != null && now - last < 6 * 3600 * 1000L) continue;
			alerted.put(k, now);

			mc.player.displayClientMessage(Component.literal(
				"§a[spangleddd stocks] §f" + tag + " BUY §e" + r.name
					+ " §7now " + Fmt.money(s.buy) + " (" + s.why + ") §f-> est. profit §a"
					+ Fmt.money(s.profitLow) + " - " + Fmt.money(s.profitHigh) + "/unit §7(/spangle)"), false);
			mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
		}
	}
}
