package io.github.firstone.framework.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Base class for feature config screens made of on/off options, built on the vanilla options list
 *
 * <p>Gives the vanilla scrolling list, tooltips and layout. Every text comes from the language file
 * ({@code assets/firstone-framework/lang/en_us.json}) under one key prefix, e.g.
 * {@code firstone-framework.animatium.}: {@code <prefix>title}, {@code <prefix><name>} and
 * {@code <prefix><name>.tooltip}. Each change is saved right away.</p>
 *
 * <p>Usage: extend it, pass the key prefix and the feature's save method, and add rows in {@link #addOptions()}
 * with {@link #addToggle}, {@link #addHeader} and {@link #addNotice}.</p>
 */
public abstract class FeatureOptionsScreen extends OptionsSubScreen {

    /** Width of a full-width row (same as a vanilla full-width option) */
    private static final int ROW_WIDTH = 310;

    /** Height of a category title row */
    private static final int HEADER_HEIGHT = 20;

    /** Prefix of every translation key of this screen */
    private final String keyPrefix;

    /** Saves the feature config after a change */
    private final Runnable saveConfig;

    /**
     * Creates a feature options screen
     *
     * @param parent     screen to return to when this screen is closed
     * @param keyPrefix  translation key prefix, ending with a dot (e.g. {@code "firstone-framework.animatium."})
     * @param saveConfig saves the feature config (e.g. {@code AnimatiumFeature::saveConfig})
     */
    protected FeatureOptionsScreen(Screen parent, String keyPrefix, Runnable saveConfig) {
        super(parent, Minecraft.getInstance().options, Component.translatable(keyPrefix + "title"));
        this.keyPrefix = keyPrefix;
        this.saveConfig = saveConfig;
    }

    /**
     * Adds an on/off option that writes the config and saves it when changed
     *
     * @param name   option name used in the translation keys ({@code <name>} and {@code <name>.tooltip})
     * @param value  current value
     * @param setter writes the new value into the config
     */
    protected void addToggle(String name, boolean value, Consumer<Boolean> setter) {
        this.list.addBig(OptionInstance.createBoolean(
            this.keyPrefix + name,
            OptionInstance.cachedConstantTooltip(Component.translatable(this.keyPrefix + name + ".tooltip")),
            value,
            newValue -> {
                setter.accept(newValue);
                this.saveConfig.run();
            }
        ));
    }

    /**
     * Adds a centered category title row
     *
     * @param name title name used in the translation key
     */
    protected void addHeader(String name) {
        this.list.addSmall(new StringWidget(ROW_WIDTH, HEADER_HEIGHT, Component.translatable(this.keyPrefix + name), this.font), null);
    }

    /**
     * Adds a centered, wrapped text row (for notes such as "singleplayer only")
     *
     * @param name text name used in the translation key
     */
    protected void addNotice(String name) {
        MultiLineTextWidget notice = new MultiLineTextWidget(Component.translatable(this.keyPrefix + name), this.font);
        notice.setMaxWidth(ROW_WIDTH).setCentered(true);
        this.list.addSmall(notice, null);
    }
}
