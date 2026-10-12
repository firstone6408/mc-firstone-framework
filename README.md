# FirstOne Framework

A framework for developing Minecraft Fabric 1.21.1 mods with a clear structure,
where new features can be added easily without modifying existing ones

By [firstone6408](https://github.com/firstone6408) · License: [LGPL-3.0](COPYING.LESSER)

---

## Features

Every feature is a separate module with its own config file and in-game settings
(Mod Menu → FirstOne Framework).

| Feature | Side | What it does |
|---|---|---|
| Animatium | Client | Minecraft 1.7.10 animations and feel: sneaking, item use and drop, re-equip, first- and third-person held items, items on the ground, hurt camera tilt, landing dust, entity movement, models, hurt tint |
| Appearance | Client | Custom game icon and window title, confirmation before the window's close button quits |
| Legacy HUD | Client | Minecraft 1.7.10 HUD: no status effect icons in the top-right corner, effect list on the left of the inventory |
| Legacy Vehicles | Client | Minecraft 1.7.10 boats: handling (with optional 1.21 ice sliding), free look and seat, mobs turning in boats, hands kept up while paddling, flat model, water splash; paddles can be hidden |
| Cosmetics | Client | Your own death effect, item break effect and music disc songs, from files in `config/firstone-framework/cosmetics/` |
| Combat Tweaks | Server | Pre-1.9 combat: no attack cooldown, no sweeping attack |
| Legacy Mechanics | Server | Minecraft 1.7.10 knockback, attack knockback, crouch hitbox, health regeneration, sword blocking, item drops, experience orbs, boat breaking and coasting |

Client features work on any server. Server features are set in game for singleplayer; a server that has the mod
uses its own files in `config/firstone-framework/`.

### Cosmetics files

Put your files in `config/firstone-framework/cosmetics/`, then press **Reload** in the Cosmetics settings (or F3+T).
An effect without files keeps the game's own effect. No image or sound ships with the mod.

```
config/firstone-framework/cosmetics/
├── death/
│   ├── particle/   0.png, 1.png, …   frames, played in number order (PNG, square, up to 256 px)
│   └── sound.ogg                     played when the body disappears
├── item_break/
│   ├── particle/   0.png, 1.png, …   frames for a breaking tool, weapon or armor piece
│   └── sound.ogg
└── music_discs/    any .ogg songs; choose one for each disc in the Music Discs screen
```

Sounds must be OGG Vorbis. With **3D Sound** on (default) they are mixed down to mono while they load, so you
hear where they come from. The death sound can start early (before the body disappears) and the start of the item
break sound can be skipped, so a sound's loudest part lands on the moment. A disc's song stops when the disc's own
time is over, as in the game.

---

## Purpose

- Organize features as independent modules that are easy to change and extend
- Clearly separate client/server logic following the Fabric architecture
- Every feature has its own config and can be configured from an in-game GUI
- Keep complexity low so a new developer can understand the structure within 10 minutes

---

## Project structure

```
src/
├── main/java/io/github/firstone/framework/
│   ├── FirstOneFramework.java          ← Main entrypoint (initializes the framework)
│   ├── common/
│   │   ├── Feature.java                ← Core interface every feature must implement
│   │   ├── FeatureRegistry.java        ← Feature registry
│   │   └── config/
│   │       └── ConfigManager.java      ← Loads/saves config JSON
│   ├── server/                         ← Server-only logic (command, data, etc.)
│   └── features/                       ← Feature modules
│       └── <feature name>/
│           ├── <Name>Feature.java      ← implement Feature interface
│           └── config/
│               └── <Name>Config.java   ← POJO config of that feature
│
└── client/java/io/github/firstone/framework/
    ├── client/
    │   ├── FirstOneFrameworkClient.java ← Client entrypoint
    │   ├── screen/
    │   │   ├── ConfigScreen.java       ← Base of every config screen (layout, Reset/Done)
    │   │   ├── ConfigList.java         ← Row types, sizes, colors, tooltip delay
    │   │   └── MainConfigScreen.java   ← Main settings GUI (feature tiles, Client / Server)
    │   └── features/                   ← Client-side feature logic
│           └── <feature name>/
│               └── <Name>ConfigScreen.java
│
└── devtest/java/io/github/firstone/framework/devtest/   ← In-game tests (development only, never in the jar)
    ├── DevTests.java                   ← List of every feature's tests
    ├── core/                           ← Test framework (world, site, checks, report, screenshots)
    ├── mixin/                          ← The test mod's accessors to Minecraft internals
    └── features/
        └── <feature name>/
            ├── <Name>Tests.java        ← That feature's tests
            └── <Name>Internals.java    ← The only calls into the feature's inner classes (if any)
```

---

## Adding a new feature

### Step 1: Create the feature folder

Create a folder in `src/main/java/.../features/<feature name>/`

### Step 2: Implement the Feature interface

```java
public class FallingTreeFeature implements Feature {

    private FallingTreeConfig config;

    @Override
    public String getId() {
        return "falling_tree";
    }

    @Override
    public void initialize() {
        config = ConfigManager.load("falling_tree.json", FallingTreeConfig.class, new FallingTreeConfig());
        // Register shared events, registries and server logic (e.g. commands)
    }

    @Override
    public void initializeClient() {
        // Register key bindings or renderers (client only)
    }

    @Override
    public void initializeDedicatedServer() {
        // Dedicated-server-only setup (not called in singleplayer)
    }
}
```

### Step 3: Register the feature

Add it in the `onInitialize()` method of `FirstOneFramework.java`:

```java
FeatureRegistry.register(new FallingTreeFeature());
```

### Step 4: Create the config

Create a POJO class for the feature's config:

```java
public class FallingTreeConfig {
    public boolean enabled = true;
    public int maxBlocks = 64;
}
```

### Step 5: Create the config GUI screen

Create `FallingTreeConfigScreen` in `src/client/java/.../features/falling_tree/client/screen/` (see
[Creating a config GUI](#creating-a-config-gui)), add its texts to `en_us.json`, and register it in
`FirstOneFrameworkClient` so `MainConfigScreen` shows it as a tile (icon, group and screen):

```java
FeatureScreenRegistry.register("falling_tree", Items.IRON_AXE, Side.SERVER, FallingTreeConfigScreen::new);
```

Use `Side.CLIENT` for features that only change the player's own game, and `Side.SERVER` for game rules decided
by the server

### Step 6: Write the dev tests

Create `FallingTreeTests` in `src/devtest/java/.../devtest/features/falling_tree/` and add it to `DevTests` (see
[Testing](#testing)). Test every option on and off, alone and together with the options it works with.

---

## Creating a config

Use `ConfigManager` to load and save configs:

```java
// Load the config (creates the default file automatically if it does not exist)
FallingTreeConfig config = ConfigManager.load(
    "falling_tree.json",
    FallingTreeConfig.class,
    new FallingTreeConfig()
);

// Save the config after changing it
ConfigManager.save("falling_tree.json", config);
```

Config files are stored in the `.minecraft/config/firstone-framework/` directory

---

## Creating a config GUI

Every config screen extends `ConfigScreen`. It provides the title, a scrolling list of compact rows, the
Reset / Done buttons and delayed tooltips, so all screens look the same. A screen only lists its rows:

```java
public class FallingTreeConfigScreen extends ConfigScreen {

    public FallingTreeConfigScreen(Screen parent) {
        super(parent, "firstone-framework.falling_tree.", FallingTreeFeature::saveConfig, FallingTreeFeature::resetConfig);
    }

    @Override
    protected void addOptions() {
        FallingTreeConfig config = FallingTreeFeature.getConfig();

        addSection("category.general");
        addToggle("enabled", config.enabled, value -> config.enabled = value);
        addToggle("drop_leaves", config.dropLeaves, value -> config.dropLeaves = value)
            .enabledWhen(() -> config.enabled);   // grayed out while "enabled" is off
    }
}
```

Available rows: `addSection`, `addToggle`, `addCycle`, `addText`, `addButton`, `addNotice` and
`addServerNotice("falling_tree.json")` (for features whose rules run on the server). Each change is written to the
config and saved right away. `resetConfig` is a static method of the feature that replaces the config with
`new FallingTreeConfig()` and saves it.

All texts come from `src/client/resources/assets/firstone-framework/lang/en_us.json`:

```json
"firstone-framework.falling_tree.name": "Falling Tree",
"firstone-framework.falling_tree.description": "Chop a whole tree at once · server rules",
"firstone-framework.falling_tree.category.general": "General",
"firstone-framework.falling_tree.enabled": "Enabled",
"firstone-framework.falling_tree.enabled.tooltip": "Breaking the bottom log fells the whole tree."
```

`name` is shown on the feature's tile in `MainConfigScreen` and as the screen title, `description` appears when
the tile is hovered; `.tooltip` keys are optional. To change how every screen looks (row width, colors, tooltip delay), edit
the constants in `ConfigList`.

---

## Testing

Features are tested in the game itself, by a separate test mod in `src/devtest`. It is for development only: the
mod jar (`./gradlew build`) never contains it.

### Running the tests

```bash
meta/scripts/run_devtest.sh                          # every feature
meta/scripts/run_devtest.sh legacy_vehicles          # one or more features
TESTS="Ice Boating" meta/scripts/run_devtest.sh legacy_vehicles   # only tests whose name contains the text
SKIP=legacy_vehicles meta/scripts/run_devtest.sh                  # every feature except these
FAST=true meta/scripts/run_devtest.sh                             # leave out the slow tests (quick check)
UPDATE_BASELINES=true meta/scripts/run_devtest.sh legacy_vehicles # accept new screenshots as the baselines
```

Fast mode leaves out the tests marked `TestCase.slow` (about 8 seconds or more); run everything before committing.
The report shows how long each test took.

The script runs `./gradlew devTest`, which starts the built mod jar in a production client (as players run it) with
its own game folder, `run/devtest/`. For each feature the test mod deletes and creates its flat world
(`devtest_<feature>`), runs its tests and closes the game when done. Do not touch the game while it runs.

Results are in `run/devtest/results/`:

- `<feature>.md` — every check of every test, with the measured and the expected value
- `summary.txt` — `PASS` or `FAIL`; the task fails when a check fails
- `screenshots/<feature>/` — screenshots of the run

The first run of a screenshot saves it as the baseline (`run/devtest/baselines/`, kept on this computer); later
runs compare against it.

### Writing tests

A feature's tests implement `FeatureTests`: its id, the configs the tests change (reset to their defaults before
every test) and a list of `TestCase`s. A test is a method that reads like a script; it runs in step with the game's
ticks, so it may read and change the client directly:

```java
private void waterTopSpeed(TestContext t) {
    t.site().pool(0, 0, 12, 90);                                   // build on the cleared test site
    Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 6.5, Boats.ON_WATER, 3.5);
    t.ride(boat);
    t.check("top speed", Boats.drive(t, boat, t.mc().options.keyUp, 100)).near(0.35, 0.001);
}
```

- `t.tick()`, `t.ticks(n)`, `t.waitUntil(...)` — let the game run
- `t.site()` — build (`fill`, `floor`, `pool`, `wall`); positions are relative to the site origin
- `t.spawn(...)`, `t.ride(...)`, `t.teleport(...)`, `t.hold(key)` — act like a player
- `t.onServer(...)`, `t.command(...)` — read or change the server
- `t.check(name, value).near(...)` / `.between(...)` / `.isTrue()` … — checks; `t.note(...)` records a value
- `t.screenshot(name, maxPercent)`, `t.saveScreenshot(name)`, `t.captureFrame()` + `Images.differingPercent(...)`
- `t.recordSounds()`, `t.particleCount(type)`, `t.resourcePack(name)`, `t.openSettings(featureId)`
- `t.pressKey(key)`, `t.holdItem(stack)`, `t.enchanted(item, enchantment, level)`
- `WindowInfo.title(...)` / `x11Property(...)` — what the window system shows (X11 with `xprop`; noted as skipped
  elsewhere)

Before each test the site is cleared, the player is put back in creative at the start and every other entity is
removed, so tests never depend on each other.

So that a refactor (moving or renaming classes) does not touch the tests:

- Test behavior from outside first: what the player sees (speeds, positions, drops, sounds, screenshots)
- Use only the feature's config and `FeatureScreenRegistry` (`t.openSettings(featureId)`) directly
- When a check really needs an inner class, call it only from `<Name>Internals.java` next to the tests; after a
  refactor only that file changes
- To read a private field of Minecraft itself (e.g. where a screen is drawn), add an accessor mixin to
  `src/devtest/.../devtest/mixin/` (the test mod's own mixins, never in the mod jar)

---

## Development guidelines

- **JavaDoc on every public class and method**, written in English
- **Class, method and variable names** in English (camelCase / PascalCase)
- **Do not add unnecessary abstraction**; keep the code simple and readable
- **Separate client/server**; never use client-only classes in the main source set
- **Features must be independent**; adding a new feature must not modify existing ones
- **Separate configs**; each feature has its own config file
- **Dev tests for every feature**; run them before committing (`meta/scripts/run_devtest.sh <feature>`)

---

## System requirements

- Minecraft 1.21.1
- Fabric Loader 0.15.11 or newer (developed with 0.19.3)
- Fabric API (developed with 0.116.12+1.21.1)
- Java 21
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu) to open the settings screen

---

## License

Copyright (C) 2026 firstone6408

FirstOne Framework is free software: you can redistribute it and/or modify it under the terms of the
GNU Lesser General Public License version 3 ([COPYING.LESSER](COPYING.LESSER)), which builds on the
GNU General Public License version 3 ([COPYING](COPYING)).
