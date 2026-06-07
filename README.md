# FirstOne Framework

Framework สำหรับพัฒนา Minecraft Fabric Mod 1.21.1 ที่มีโครงสร้างชัดเจน
รองรับการขยาย Feature ใหม่ได้ง่ายโดยไม่ต้องแก้ไข Feature เดิม

---

## จุดประสงค์ของ Mod

- จัดระบบ Feature ให้เป็นโมดูลอิสระ แก้ไขและขยายได้ง่าย
- แยก client/server logic อย่างชัดเจนตาม Fabric architecture
- ทุก Feature มี Config ของตัวเองและรองรับการตั้งค่าผ่าน GUI ในเกม
- ลด complexity ให้นักพัฒนาใหม่เข้าใจโครงสร้างได้ภายใน 10 นาที

---

## โครงสร้างโปรเจกต์

```
src/
├── main/java/io/github/firstone/framework/
│   ├── FirstOneFramework.java          ← Main entrypoint (เริ่มต้น Framework)
│   ├── common/
│   │   ├── Feature.java                ← Interface หลักที่ทุก Feature ต้อง implement
│   │   ├── FeatureRegistry.java        ← ระบบลงทะเบียน Feature
│   │   └── config/
│   │       └── ConfigManager.java      ← โหลด/บันทึก Config JSON
│   ├── server/                         ← Server-only logic (command, data, etc.)
│   └── features/                       ← Feature modules
│       └── <ชื่อ Feature>/
│           ├── <Name>Feature.java      ← implement Feature interface
│           └── config/
│               └── <Name>Config.java   ← POJO config ของ Feature นั้น
│
└── client/java/io/github/firstone/framework/
    ├── client/
    │   ├── FirstOneFrameworkClient.java ← Client entrypoint
    │   ├── screen/
    │   │   └── MainConfigScreen.java   ← GUI หลักสำหรับตั้งค่า
    │   └── features/                   ← Client-side feature logic
│           └── <ชื่อ Feature>/
│               └── <Name>ConfigScreen.java
```

---

## วิธีเพิ่ม Feature ใหม่

### ขั้นตอนที่ 1: สร้าง Feature Folder

สร้างโฟลเดอร์ใน `src/main/java/.../features/<ชื่อ Feature>/`

### ขั้นตอนที่ 2: Implement Feature Interface

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
        // ลงทะเบียน Event หรือ Registry ที่ใช้ร่วมกัน
    }

    @Override
    public void initializeClient() {
        // ลงทะเบียน KeyBinding หรือ Renderer (client only)
    }

    @Override
    public void initializeServer() {
        // ลงทะเบียน Command หรือ Server logic
    }
}
```

### ขั้นตอนที่ 3: ลงทะเบียน Feature

เพิ่มในเมธอด `onInitialize()` ของ `FirstOneFramework.java`:

```java
FeatureRegistry.register(new FallingTreeFeature());
```

### ขั้นตอนที่ 4: สร้าง Config

สร้าง POJO class สำหรับ Config ของ Feature:

```java
public class FallingTreeConfig {
    public boolean enabled = true;
    public int maxBlocks = 64;
}
```

### ขั้นตอนที่ 5: สร้าง Config GUI Screen

สร้าง `FallingTreeConfigScreen` ใน `src/client/java/.../client/features/falling_tree/`
แล้วเชื่อมกับ `MainConfigScreen` ผ่านเมธอด `onFeatureButtonClick()`

---

## วิธีสร้าง Config

ใช้ `ConfigManager` ในการโหลดและบันทึก Config:

```java
// โหลด Config (สร้างไฟล์ default อัตโนมัติหากยังไม่มี)
FallingTreeConfig config = ConfigManager.load(
    "falling_tree.json",
    FallingTreeConfig.class,
    new FallingTreeConfig()
);

// บันทึก Config หลังแก้ไข
ConfigManager.save("falling_tree.json", config);
```

ไฟล์ Config จะถูกเก็บไว้ใน `.minecraft/config/` directory

---

## วิธีสร้าง GUI Config

แต่ละ Feature สามารถมี Config Screen ของตัวเองได้:

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

จากนั้น link จาก `MainConfigScreen.onFeatureButtonClick()`:

```java
private void onFeatureButtonClick(Feature feature) {
    if (feature instanceof FallingTreeFeature f) {
        this.minecraft.setScreen(new FallingTreeConfigScreen(this, f.getConfig()));
    }
}
```

---

## แนวทางการพัฒนา

- **JavaDoc ทุก public class และ method** เขียนเป็นภาษาไทย
- **ชื่อ class, method, variable** ใช้ภาษาอังกฤษ (camelCase / PascalCase)
- **อย่าสร้าง abstraction เกินความจำเป็น** เขียนให้ simple และอ่านง่าย
- **แยก client/server** อย่าใช้ client-only class ใน main source set
- **Feature ต้องอิสระจากกัน** การเพิ่ม Feature ใหม่ต้องไม่แก้ไข Feature เดิม
- **Config แยกต่างหาก** แต่ละ Feature มีไฟล์ Config ของตัวเอง

---

## ข้อกำหนดระบบ

- Minecraft 1.21.1
- Fabric Loader >= 0.19.3
- Fabric API 0.116.12+1.21.1
- Java 21
