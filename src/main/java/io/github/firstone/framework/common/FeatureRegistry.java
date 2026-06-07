package io.github.firstone.framework.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ระบบลงทะเบียน Feature ทั้งหมดในระบบ
 *
 * <p>ใช้สำหรับเพิ่ม Feature เข้าสู่ Framework และดึงรายการ Feature
 * ทั้งหมดสำหรับการเรียกใช้งาน initialize ต่างๆ</p>
 *
 * <p>วิธีใช้งาน: เรียก {@link #register(Feature)} จาก entrypoint หลัก
 * ก่อนที่ Minecraft จะโหลด Feature นั้นๆ</p>
 *
 * <pre>{@code
 * // ตัวอย่างการลงทะเบียน Feature
 * FeatureRegistry.register(new FallingTreeFeature());
 * FeatureRegistry.register(new VeinMinerFeature());
 * }</pre>
 */
public final class FeatureRegistry {

    private static final List<Feature> FEATURES = new ArrayList<>();

    private FeatureRegistry() {}

    /**
     * ลงทะเบียน Feature เข้าสู่ระบบ
     *
     * <p>ต้องเรียกก่อนที่ Framework จะเรียก initialize เสมอ
     * โดยปกติเรียกจาก {@code ModInitializer.onInitialize()}</p>
     *
     * @param feature Feature ที่ต้องการลงทะเบียน
     * @throws IllegalArgumentException หาก Feature นั้นมี ID ซ้ำกับที่ลงทะเบียนไว้แล้ว
     */
    public static void register(Feature feature) {
        String id = feature.getId();
        for (Feature existing : FEATURES) {
            if (existing.getId().equals(id)) {
                throw new IllegalArgumentException("Duplicate feature ID: " + id);
            }
        }
        FEATURES.add(feature);
    }

    /**
     * คืนค่า Feature ทั้งหมดที่ถูกลงทะเบียน
     *
     * <p>รายการที่ได้เป็นแบบอ่านอย่างเดียว ไม่สามารถแก้ไขได้โดยตรง</p>
     *
     * @return รายการ Feature ทั้งหมด (อ่านอย่างเดียว)
     */
    public static List<Feature> getAll() {
        return Collections.unmodifiableList(FEATURES);
    }

}
