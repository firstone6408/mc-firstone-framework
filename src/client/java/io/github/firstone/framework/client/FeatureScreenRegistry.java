package io.github.firstone.framework.client;

import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * ระบบลงทะเบียน Config Screen สำหรับแต่ละ Feature
 *
 * <p>ใช้สำหรับเชื่อม Feature ID กับ Screen ของ Config
 * เพื่อให้ {@link io.github.firstone.framework.client.screen.MainConfigScreen}
 * สามารถเปิดหน้าจอตั้งค่าของ Feature แต่ละตัวได้</p>
 *
 * <p>วิธีใช้งาน:</p>
 * <pre>{@code
 * // ลงทะเบียน Config Screen ของ Feature
 * FeatureScreenRegistry.register("my_feature", parent -> new MyFeatureConfigScreen(parent));
 * }</pre>
 */
public final class FeatureScreenRegistry {

    private static final Map<String, Function<Screen, Screen>> SCREENS = new HashMap<>();

    private FeatureScreenRegistry() {}

    /**
     * ลงทะเบียน Config Screen สำหรับ Feature
     *
     * <p>ต้องเรียกจาก {@code ClientModInitializer} ก่อนที่ผู้เล่นจะเปิด Config Screen</p>
     *
     * @param featureId ID ของ Feature เช่น "animatium"
     * @param factory   Factory Function ที่รับ parent Screen และคืน Config Screen ใหม่
     */
    public static void register(String featureId, Function<Screen, Screen> factory) {
        SCREENS.put(featureId, factory);
    }

    /**
     * สร้าง Config Screen สำหรับ Feature ที่ระบุ
     *
     * @param featureId ID ของ Feature
     * @param parent    Screen ที่จะกลับไปเมื่อปิด Config Screen
     * @return Config Screen ของ Feature หรือ {@code null} หากไม่ได้ลงทะเบียนไว้
     */
    public static Screen createScreen(String featureId, Screen parent) {
        Function<Screen, Screen> factory = SCREENS.get(featureId);
        return factory != null ? factory.apply(parent) : null;
    }
}
