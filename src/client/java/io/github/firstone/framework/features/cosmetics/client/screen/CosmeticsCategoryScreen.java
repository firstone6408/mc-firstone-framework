package io.github.firstone.framework.features.cosmetics.client.screen;

import io.github.firstone.framework.client.screen.ConfigList;
import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.features.cosmetics.CosmeticsFeature;
import io.github.firstone.framework.features.cosmetics.CosmeticsFiles;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.util.Locale;

/**
 * Base of the Cosmetics category screens: the file status line and the slider texts they share
 *
 * <p>Texts come from {@code firstone-framework.cosmetics.<category>.*}; the status texts from
 * {@code firstone-framework.cosmetics.status*}.</p>
 */
public abstract class CosmeticsCategoryScreen extends ConfigScreen {

    /** Color of the file status lines (gray) */
    private static final int STATUS_COLOR = 0xFFA0A0A0;

    /**
     * Creates a category screen
     *
     * @param parent   screen to return to when this screen is closed
     * @param category key of the category, e.g. {@code "death"}
     * @param reset    restores the defaults of this category only (then the config is saved)
     */
    protected CosmeticsCategoryScreen(Screen parent, String category, Runnable reset) {
        super(parent, "firstone-framework.cosmetics." + category + ".", CosmeticsFeature::saveConfig, () -> {
            reset.run();
            CosmeticsFeature.saveConfig();
        });
    }

    /**
     * Adds the line that tells which files of an effect are in its folder
     *
     * @param effect {@link CosmeticsFiles#DEATH} or {@link CosmeticsFiles#ITEM_BREAK}
     */
    protected void addEffectStatus(String effect) {
        int frames = CosmeticsFiles.frames(effect).size();
        boolean sound = Files.isRegularFile(CosmeticsFiles.sound(effect));
        Component files = null;
        if (frames > 0 && sound) {
            files = Component.empty().append(status("frames", frames)).append(" + ").append(status("sound"));
        } else if (frames > 0) {
            files = status("frames", frames);
        } else if (sound) {
            files = status("sound");
        }
        addStatus(files);
    }

    /**
     * Adds a gray status line: "Files: …", or "No files" when {@code files} is null
     *
     * @param files what was found, or null
     */
    protected void addStatus(@Nullable Component files) {
        Component label = files != null ? status("", files) : status("none");
        addRow(new ConfigList.OptionRow(label, STATUS_COLOR, status("tooltip"), null));
    }

    /**
     * Returns a status text ({@code firstone-framework.cosmetics.status.<name>})
     *
     * @param name key after {@code status.}, or "" for {@code status} itself
     * @param args arguments of the text
     * @return the text
     */
    protected static Component status(String name, Object... args) {
        return Component.translatable("firstone-framework.cosmetics.status" + (name.isEmpty() ? "" : "." + name), args);
    }

    /**
     * Slider text of a count
     *
     * @param value the value
     * @return e.g. "20"
     */
    protected static Component count(double value) {
        return Component.literal(Long.toString(Math.round(value)));
    }

    /**
     * Slider text of a multiplier
     *
     * @param value the value
     * @return e.g. "1.00x"
     */
    protected static Component times(double value) {
        return Component.literal(String.format(Locale.ROOT, "%.2fx", value));
    }

    /**
     * Slider text of a volume
     *
     * @param value the value, from 0 to 1
     * @return e.g. "100%"
     */
    protected static Component percent(double value) {
        return Component.literal(Math.round(value * 100) + "%");
    }

    /**
     * Slider text of a time
     *
     * @param value the value in seconds
     * @return e.g. "0.40 s"
     */
    protected static Component seconds(double value) {
        return Component.literal(String.format(Locale.ROOT, "%.2f s", value));
    }
}
