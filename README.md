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
| Animatium | Client | Minecraft 1.7.10 animations and feel: sneaking, item use and drop, re-equip, entity movement, models, hurt tint |
| Appearance | Client | Custom game icon and window title, confirmation before the window's close button quits |
| Legacy HUD | Client | Minecraft 1.7.10 HUD: no status effect icons in the top-right corner, effect list on the left of the inventory |
| Cosmetics | Client | Your own death effect, item break effect and music disc songs, from files in `config/firstone-framework/cosmetics/` |
| Combat Tweaks | Server | Pre-1.9 combat: no attack cooldown, no sweeping attack |
| Legacy Mechanics | Server | Minecraft 1.7.10 knockback, attack knockback and crouch hitbox |

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

## Development guidelines

- **JavaDoc on every public class and method**, written in English
- **Class, method and variable names** in English (camelCase / PascalCase)
- **Do not add unnecessary abstraction**; keep the code simple and readable
- **Separate client/server**; never use client-only classes in the main source set
- **Features must be independent**; adding a new feature must not modify existing ones
- **Separate configs**; each feature has its own config file

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
