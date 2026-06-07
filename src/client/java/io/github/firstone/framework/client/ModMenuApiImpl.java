package io.github.firstone.framework.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.firstone.framework.client.screen.MainConfigScreen;

/**
 * เชื่อมต่อ Framework กับ Mod Menu
 *
 * <p>ลงทะเบียน {@link MainConfigScreen} เป็นหน้าจอ Config ของ Mod
 * ทำให้ผู้เล่นสามารถเปิดหน้าตั้งค่าได้จากรายการ Mod ใน Mod Menu</p>
 *
 * <p>คลาสนี้จะถูกเรียกโดย Mod Menu เท่านั้น หากไม่ได้ติดตั้ง Mod Menu
 * คลาสนี้จะไม่ถูก load และ Mod ยังคงทำงานได้ปกติ</p>
 */
public class ModMenuApiImpl implements ModMenuApi {

    /**
     * คืนค่า factory สำหรับสร้างหน้าจอ Config ของ Mod
     *
     * @return factory ที่สร้าง {@link MainConfigScreen} พร้อม parent screen
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return MainConfigScreen::new;
    }
}
