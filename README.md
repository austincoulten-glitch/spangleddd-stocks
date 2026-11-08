# spangleddd stocks (scratch version) - Fabric 1.21.11, client-side

Passively records DonutSMP auction house prices, graphs this week vs last week per item,
flags short-term and long-term buys with an estimated profit range, and alerts you in chat + sound.

## Build the jar
**Option A - locally (needs JDK 21 and Gradle 9+):**
    gradle build
Jar: `build/libs/spangleddd-stocks-1.0.0.jar` (not the `-sources` one). Drop it in `.minecraft/mods`
with Fabric Loader + Fabric API for 1.21.11.

**Option B - no setup:** push this folder to a GitHub repo. The Actions tab builds it
(`.github/workflows/build.yml`); download the `spangleddd-stocks-jar` artifact.

If the build complains about versions, update `gradle.properties` from https://fabricmc.net/develop
(loader_version, loom_version, fabric_version).

## Use
1. Browse `/ah` normally (search an item, sort lowest price). Prices are saved automatically every few seconds.
2. `/spangle` (or `/stocks`) opens the GUI.
3. Tabs: Short-term (dip buys), Long-term (steady uptrends), All items.
4. Drag the MIN / MAX slider to filter by unit price. Fee +/- sets the AH cut used in profit estimates.
5. Gold dot / "BUY NOW" = strong signal. Alerts toggle in the header.

Data: `.minecraft/config/spangleddd_stocks/` (prices.json keeps ~15 days; settings.json).

## Notes
- It only reads the screen you already opened. It sends nothing and clicks nothing.
- A week of history only exists after you've browsed for a week. Signals need ~12h of data per item.
- Price = lowest listing seen per 30 minutes, per unit. Enchanted/damaged items are skipped.
- Estimates are heuristics from your own observations, not guarantees.
- AH lore parsing expects a "Price: $1.5K" line plus a Seller / Expires line. If DonutSMP words it
  differently, edit the two regexes at the top of `AhScanner.java`.
