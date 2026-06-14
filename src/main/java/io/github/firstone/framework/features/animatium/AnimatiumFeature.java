package io.github.firstone.framework.features.animatium;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;

/**
 * Feature หลักของ Animatium - ระบบ Animation แบบ Legacy เหมือน version ก่อน 1.9
 *
 * <p>Feature นี้ทำงานฝั่ง Client เท่านั้น ประกอบด้วย 3 Animation:</p>
 * <ul>
 *   <li>No Re-equip Animation - ไม่มีการหน่วงเมื่อสลับ Item</li>
 *   <li>Old Sneak Animation - ย่อตัวทันทีเมื่อกด Shift</li>
 *   <li>Old Item Drop Animation - ไม่มีท่าทางโยนเมื่อทิ้ง Item</li>
 * </ul>
 *
 * <p>การตั้งค่าแต่ละอย่างสามารถเปิด/ปิดได้จาก Mod Menu หรือ Config Screen</p>
 */
public class AnimatiumFeature implements Feature {

    /** ชื่อไฟล์ Config ที่ใช้บันทึกการตั้งค่า */
    private static final String CONFIG_FILE = "animatium.json";

    /** Config ปัจจุบันของ Feature นี้ เริ่มต้นด้วยค่า default */
    private static AnimatiumConfig config = new AnimatiumConfig();

    @Override
    public String getId() {
        return "animatium";
    }

    @Override
    public String getDisplayName() {
        return "Animatium — Legacy Animations";
    }

    @Override
    public String getDescription() {
        return "Legacy animations and combat feel from Minecraft pre-1.9\nIncludes: No Re-equip, Old Sneak, Old Item Drop, No Sweep Effect, No Damage Indicator, No Attack Sounds";
    }

    @Override
    public void initialize() {
        config = ConfigManager.load(CONFIG_FILE, AnimatiumConfig.class, new AnimatiumConfig());
    }

    /**
     * คืนค่า Config ปัจจุบันของ Animatium
     *
     * <p>ใช้โดย Mixin และ Config Screen เพื่ออ่านและแก้ไขการตั้งค่า</p>
     *
     * @return Config ปัจจุบัน ไม่เป็น null
     */
    public static AnimatiumConfig getConfig() {
        return config;
    }

    /**
     * บันทึก Config ปัจจุบันลงไฟล์ JSON
     *
     * <p>เรียกหลังจากผู้เล่นเปลี่ยนการตั้งค่าใน Config Screen</p>
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }
}
