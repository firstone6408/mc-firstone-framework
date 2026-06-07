package io.github.firstone.framework.client;

import io.github.firstone.framework.FirstOneFramework;
import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.FeatureRegistry;
import net.fabricmc.api.ClientModInitializer;

/**
 * จุดเริ่มต้นของ FirstOne Framework ฝั่ง Client
 *
 * <p>รับผิดชอบการเรียก {@link Feature#initializeClient()} สำหรับ Feature ทั้งหมด
 * รวมถึงการลงทะเบียน KeyBinding สำหรับเปิดหน้าจอ Config หลัก</p>
 *
 * <p>จะถูกเรียกเฉพาะบน Client เท่านั้น ไม่ถูกเรียกบน Dedicated Server</p>
 */
public class FirstOneFrameworkClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FirstOneFramework.LOGGER.info("FirstOne Framework initializing client...");

        for (Feature feature : FeatureRegistry.getAll()) {
            feature.initializeClient();
        }

        FirstOneFramework.LOGGER.info("FirstOne Framework client ready");
    }
}
