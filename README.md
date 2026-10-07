# FirstOne Framework

A framework for developing Minecraft Fabric 1.21.1 mods with a clear structure,
where new features can be added easily without modifying existing ones

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
    │   │   └── MainConfigScreen.java   ← Main settings GUI
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

Create `FallingTreeConfigScreen` in `src/client/java/.../features/falling_tree/client/screen/`
and register it in `FirstOneFrameworkClient` with `FeatureScreenRegistry.register()` (the factory receives the parent screen) so `MainConfigScreen` can open it

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

Each feature can have its own config screen:

```java
public class FallingTreeConfigScreen extends Screen {

    private final Screen parent;
    private final FallingTreeConfig config;

    public FallingTreeConfigScreen(Screen parent, FallingTreeConfig config) {
        super(Component.literal("Falling Tree Settings"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    public void onClose() {
        ConfigManager.save("falling_tree.json", config);
        this.minecraft.setScreen(parent);
    }
}
```

Before `FeatureScreenRegistry` existed, screens were linked manually from `MainConfigScreen.onFeatureButtonClick()` like this
(`onFeatureButtonClick()` now opens the screen registered in `FeatureScreenRegistry` instead):

```java
private void onFeatureButtonClick(Feature feature) {
    if (feature instanceof FallingTreeFeature f) {
        this.minecraft.setScreen(new FallingTreeConfigScreen(this, f.getConfig()));
    }
}
```

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
- Fabric Loader >= 0.19.3
- Fabric API 0.116.12+1.21.1
- Java 21
