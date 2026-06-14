package io.github.firstone.framework.features.animatium;

/**
 * คลาสเก็บค่าการตั้งค่าของ Feature Animatium
 *
 * <p>ค่าทุกอย่างในคลาสนี้จะถูกบันทึกลงไฟล์ JSON และโหลดขึ้นมาเมื่อเปิดเกม</p>
 *
 * <p>ค่าเริ่มต้นทุกตัวเปิดใช้งาน (true) เพื่อให้เป็น Legacy Animation ทันที</p>
 */
public class AnimatiumConfig {

    /**
     * ปิดการใช้งาน Re-equip Animation เหมือน version เก่าก่อน 1.9
     *
     * <p>เมื่อเปิดใช้งาน: การสลับ Item ใน Hotbar จะไม่มี Animation หน่วงลงมา</p>
     * <p>พฤติกรรมเดิม: Item จะหน่วงลงและขึ้นมาเมื่อสลับ</p>
     */
    public boolean noReequipAnimation = true;

    /**
     * เปิดใช้งาน Old Sneak Animation เหมือน version เก่าก่อน 1.9
     *
     * <p>เมื่อเปิดใช้งาน: การกด Shift จะย่อตัวทันทีโดยไม่มี Transition</p>
     * <p>พฤติกรรมเดิม: มี Animation หน่วงเมื่อกด Shift</p>
     */
    public boolean oldSneakAnimation = true;

    /**
     * เปิดใช้งาน Old Item Drop Animation เหมือน version เก่าก่อน 1.9
     *
     * <p>เมื่อเปิดใช้งาน: การทิ้ง Item จะไม่มีท่าทางการโยน แขนจะอยู่นิ่ง</p>
     * <p>พฤติกรรมเดิม: มี Animation แกว่งแขนเมื่อทิ้ง Item</p>
     */
    public boolean oldItemDropAnimation = true;

    /**
     * ซ่อน Sweep Attack Effect เหมือน version เก่าก่อน 1.9
     *
     * <p>เมื่อเปิดใช้งาน: การโจมตีพัดจะไม่แสดง Particle กวาด และไม่มีเสียง PLAYER_ATTACK_SWEEP</p>
     * <p>พฤติกรรมเดิม: มี Animation Particle กวาดและเสียงพิเศษเมื่อโจมตีพัด</p>
     */
    public boolean noSweepEffect = true;

    /**
     * ซ่อน Damage Indicator Particle เหมือน version เก่าก่อน 1.9
     *
     * <p>เมื่อเปิดใช้งาน: การโจมตีจะไม่แสดง Particle แสดงปริมาณ Damage</p>
     * <p>พฤติกรรมเดิม: มี Particle เล็กๆ ลอยขึ้นเมื่อโจมตีสำเร็จ</p>
     */
    public boolean noDamageIndicatorParticle = true;

    /**
     * ปิดเสียง Attack Sounds เหมือน version เก่าก่อน 1.9
     *
     * <p>เมื่อเปิดใช้งาน: การโจมตีจะไม่มีเสียง CRIT, STRONG, WEAK, NODAMAGE, KNOCKBACK</p>
     * <p>พฤติกรรมเดิม: มีเสียงโจมตีหลากหลายประเภทขึ้นกับผลลัพธ์การโจมตี</p>
     */
    public boolean noAttackSounds = true;
}
