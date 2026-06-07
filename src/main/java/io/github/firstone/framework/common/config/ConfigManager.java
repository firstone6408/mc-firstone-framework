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
 * Saves and loads each feature's config as a JSON file
 *
 * <p>All config files are stored in Minecraft's config/ directory,
 * using Gson to convert objects to JSON and back</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Load the config (creates the file with defaults if it does not exist yet)
 * MyConfig config = ConfigManager.load("my_feature.json", MyConfig.class, new MyConfig());
 *
 * // Save the config
 * ConfigManager.save("my_feature.json", config);
 * }</pre>
 */
public final class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("firstone-framework");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigManager() {}

    /**
     * Loads a config from a JSON file
     *
     * <p>If the config file does not exist, a new file is created from the given default value.
     * If reading the file fails with an I/O error, the error is logged and the default value is returned (and written to the file)</p>
     *
     * @param <T>          config type
     * @param filename     config file name, e.g. "falling_tree.json"
     * @param type         config class
     * @param defaultValue default value used when the file does not exist
     * @return the config loaded from the file, or the default value if loading failed
     */
    public static <T> T load(String filename, Class<T> type, T defaultValue) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(filename);

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
     * Saves a config to a JSON file
     *
     * <p>Creates the file if it does not exist and overwrites it if it does.
     * If saving fails, an error is logged</p>
     *
     * @param filename config file name, e.g. "falling_tree.json"
     * @param config   config object to save
     */
    public static void save(String filename, Object config) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(filename);

        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config file {}: {}", filename, e.getMessage());
        }
    }
}
