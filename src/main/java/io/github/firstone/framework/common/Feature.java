package io.github.firstone.framework.common;

/**
 * อินเตอร์เฟซหลักที่ Feature ทุกอย่างต้องนำไปใช้งาน
 *
 * <p>ทุก Feature ในระบบต้องสืบทอดอินเตอร์เฟซนี้และ implement เมธอดทั้งหมด
 * เพื่อให้ Framework สามารถจัดการวงจรชีวิตของ Feature ได้อย่างเป็นระบบ</p>
 *
 * <p>วิธีใช้งาน: สร้างคลาสใหม่ใน features/&lt;ชื่อ Feature&gt;/ แล้ว implement อินเตอร์เฟซนี้
 * จากนั้นลงทะเบียนใน {@link FeatureRegistry} จาก entrypoint หลัก</p>
 */
public interface Feature {

    /**
     * คืนค่า ID เฉพาะของ Feature นี้
     *
     * <p>ID ต้องไม่ซ้ำกับ Feature อื่น และควรใช้รูปแบบ snake_case</p>
     *
     * @return ID ของ Feature เช่น "falling_tree" หรือ "vein_miner"
     */
    String getId();

    /**
     * คืนชื่อที่แสดงใน GUI ของ Feature นี้
     *
     * <p>ใช้แสดงบนปุ่มใน {@link io.github.firstone.framework.client.screen.MainConfigScreen}
     * หาก Feature ไม่ override จะใช้ ID แทน</p>
     *
     * @return ชื่อสำหรับแสดงผล เช่น "Animatium" หรือ "Falling Tree"
     */
    default String getDisplayName() {
        return getId();
    }

    /**
     * คืนคำอธิบายสั้นๆ ของ Feature นี้ สำหรับแสดงเมื่อ hover บนปุ่ม
     *
     * <p>หาก Feature ไม่ override จะไม่มี tooltip แสดง</p>
     *
     * @return คำอธิบาย หรือ {@code null} หากไม่ต้องการ tooltip
     */
    default String getDescription() {
        return null;
    }

    /**
     * เริ่มต้น Feature สำหรับทั้ง client และ server (common logic)
     *
     * <p>เมธอดนี้ถูกเรียกจาก {@code ModInitializer} ระหว่างการโหลด Mod
     * เหมาะสำหรับการลงทะเบียน Event, Block, Item หรือ Registry อื่นๆ
     * ที่ใช้ร่วมกันทั้งสองฝั่ง</p>
     */
    void initialize();

    /**
     * เริ่มต้น Feature สำหรับ client เท่านั้น
     *
     * <p>เมธอดนี้ถูกเรียกจาก {@code ClientModInitializer} บน client เท่านั้น
     * เหมาะสำหรับ Screen, Renderer, KeyBinding หรือ client-only Event
     * จะไม่ถูกเรียกบน Dedicated Server</p>
     */
    default void initializeClient() {}

    /**
     * เริ่มต้น Feature สำหรับ server เท่านั้น
     *
     * <p>เมธอดนี้ถูกเรียกหลัง {@link #initialize()} บน Dedicated Server
     * เหมาะสำหรับ Command, Persistent Data หรือ server-only logic</p>
     */
    default void initializeServer() {}
}
