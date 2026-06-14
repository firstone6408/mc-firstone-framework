package io.github.firstone.framework.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * จัดการการบันทึกและโหลด Config ของแต่ละ Feature จากไฟล์ JSON
 *
 * <p>Config ทุกไฟล์จะถูกเก็บไว้ใน {@code config/firstone-framework/} directory ของ Minecraft
 * โดยใช้ Gson ในการแปลง Object เป็น JSON และในทางกลับกัน</p>
 *
 * <p>วิธีใช้งาน:</p>
 * <pre>{@code
 * // โหลด Config (สร้างไฟล์ default หากยังไม่มี)
 * MyConfig config = ConfigManager.load("my_feature.json", MyConfig.class, new MyConfig());
 *
 * // บันทึก Config
 * ConfigManager.save("my_feature.json", config);
 * }</pre>
 */
public final class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("firstone-framework");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** โฟลเดอร์ย่อยภายใน config/ ที่ใช้เก็บ Config ทั้งหมดของ mod นี้ */
    private static final String CONFIG_FOLDER = "firstone-framework";

    private ConfigManager() {}

    /**
     * โหลด Config จากไฟล์ JSON
     *
     * <p>หากไม่มีไฟล์ Config จะสร้างไฟล์ใหม่โดยใช้ค่า default ที่กำหนด
     * หากโหลดไฟล์ไม่สำเร็จจะคืนค่า default และบันทึก log error</p>
     *
     * @param <T>          ประเภทของ Config
     * @param filename     ชื่อไฟล์ Config เช่น "falling_tree.json"
     * @param type         Class ของ Config
     * @param defaultValue ค่า default ที่ใช้หากไม่มีไฟล์
     * @return Config ที่โหลดจากไฟล์ หรือค่า default หากโหลดไม่สำเร็จ
     */
    public static <T> T load(String filename, Class<T> type, T defaultValue) {
        Path configPath = resolveConfigPath(filename);

        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                T loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load config file {}: {}", filename, e.getMessage());
            }
        }

        save(filename, defaultValue);
        return defaultValue;
    }

    /**
     * บันทึก Config ลงไฟล์ JSON
     *
     * <p>สร้างไฟล์ใหม่หากยังไม่มี และเขียนทับหากมีอยู่แล้ว
     * หากบันทึกไม่สำเร็จจะบันทึก log error</p>
     *
     * @param filename ชื่อไฟล์ Config เช่น "falling_tree.json"
     * @param config   Object Config ที่ต้องการบันทึก
     */
    public static void save(String filename, Object config) {
        Path configPath = resolveConfigPath(filename);

        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config file {}: {}", filename, e.getMessage());
        }
    }

    /**
     * คืน Path เต็มของไฟล์ Config และสร้าง folder {@code firstone-framework/} หากยังไม่มี
     *
     * @param filename ชื่อไฟล์ Config เช่น "animatium.json"
     * @return Path ของไฟล์ที่อยู่ใน {@code config/firstone-framework/}
     */
    private static Path resolveConfigPath(String filename) {
        Path folder = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FOLDER);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory {}: {}", folder, e.getMessage());
        }
        return folder.resolve(filename);
    }
}
