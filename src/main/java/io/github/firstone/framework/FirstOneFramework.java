package io.github.firstone.framework;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.FeatureRegistry;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * จุดเริ่มต้นหลักของ FirstOne Framework
 *
 * <p>รับผิดชอบการเริ่มต้น Framework และเรียก {@link Feature#initialize()}
 * พร้อมกับ {@link Feature#initializeServer()} สำหรับ Feature ทั้งหมดที่ลงทะเบียนไว้</p>
 *
 * <p>การลงทะเบียน Feature ทำได้โดยเรียก {@code FeatureRegistry.register()} ก่อน
 * {@code super.onInitialize()} หรือจากที่นี่โดยตรง</p>
 */
public class FirstOneFramework implements ModInitializer {

    /** ID หลักของ Mod ใช้สำหรับ namespace และ Logger */
    public static final String MOD_ID = "firstone-framework";

    /** Logger สำหรับ Framework ทั้งหมด */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("FirstOne Framework initializing...");

        List<Feature> features = FeatureRegistry.getAll();
        for (Feature feature : features) {
            feature.initialize();
            feature.initializeServer();
            LOGGER.info("Initialized feature: {}", feature.getId());
        }

        LOGGER.info("FirstOne Framework ready ({} features loaded)", features.size());
    }
}
