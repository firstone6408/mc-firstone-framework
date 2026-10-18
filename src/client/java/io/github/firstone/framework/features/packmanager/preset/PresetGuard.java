package io.github.firstone.framework.features.packmanager.preset;

import io.github.firstone.framework.client.screen.Questions;
import io.github.firstone.framework.features.packmanager.PackLists;
import io.github.firstone.framework.features.packmanager.PackManagerConfig;
import io.github.firstone.framework.features.packmanager.PackManagerFeature;
import io.github.firstone.framework.features.packmanager.client.screen.PresetListScreen;
import io.github.firstone.framework.features.packmanager.preset.PresetStore.PackPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Asks before a change in the Resource Packs screen leaves the preset in use ("Ask Before Leaving a Preset")
 *
 * <p>While the Selected list matches a preset, the screen shows "Preset: name". The first change the player makes
 * (turning a pack on or off, moving it with an arrow or Shift+arrow key, dropping a dragged pack) asks first:
 * "Change Packs" makes the change and the packs become custom; "Cancel" changes nothing. The preset itself never
 * changes. {@code PackEntryMixin} stops the screen's own changes; a drop asks once for all its steps
 * ({@link #change}). Applying a preset, the screen that chooses a preset's packs and data pack screens never ask.</p>
 */
public final class PresetGuard {

    private static final String KEY = "firstone-framework.pack_manager.leave_preset.";

    /** True while a change runs that needs no question (allowed by the player, or a preset being applied) */
    private static boolean allowed;

    private PresetGuard() {}

    /**
     * Stops a change of the open Resource Packs screen and asks first, if it would leave the preset in use
     *
     * <p>Called by {@code PackEntryMixin} before the screen turns a pack on or off or moves it.</p>
     *
     * @param change makes the change again once the player allows it
     * @return true if the change was stopped (the question is open), false to let it happen now
     */
    public static boolean intercept(Runnable change) {
        if (allowed) {
            return false;
        }
        PackSelectionScreen screen = askingScreen();
        PackPreset preset = screen == null ? null : presetInUse(screen);
        if (screen == null || preset == null) {
            return false;
        }
        ask(screen, preset, change);
        return true;
    }

    /**
     * Makes a change of several steps (a dropped pack), asking once first if it would leave the preset in use
     *
     * @param screen the pack screen
     * @param change the change
     */
    public static void change(PackSelectionScreen screen, Runnable change) {
        PackPreset preset = asks(screen) ? presetInUse(screen) : null;
        if (preset == null) {
            run(change);
        } else {
            ask(screen, preset, change);
        }
    }

    /**
     * Makes a change without asking (applying a preset, or a change the player allowed)
     *
     * @param change the change
     */
    public static void run(Runnable change) {
        boolean before = allowed;
        allowed = true;
        try {
            change.run();
        } finally {
            allowed = before;
        }
    }

    /** Returns the preset the screen's Selected list matches, once the files to compare are read */
    @Nullable
    private static PackPreset presetInUse(PackSelectionScreen screen) {
        PackFiles.awaitAll(PresetStore.getPresets());
        return PresetStatus.current(PackLists.model(screen));
    }

    /** Returns the open screen if it is a Resource Packs screen that asks, else null */
    @Nullable
    private static PackSelectionScreen askingScreen() {
        return Minecraft.getInstance().screen instanceof PackSelectionScreen screen && asks(screen) ? screen : null;
    }

    /** Tells whether changes in a pack screen ask before leaving a preset */
    private static boolean asks(PackSelectionScreen screen) {
        PackManagerConfig config = PackManagerFeature.getConfig();
        return config.presets && config.askBeforeLeavingPreset && PresetListScreen.isOffered(screen);
    }

    /** Shows the question; "Change Packs" goes back to the screen and makes the change */
    private static void ask(PackSelectionScreen screen, PackPreset preset, Runnable change) {
        Questions.ask(screen, Component.translatable(KEY + "title", preset.name),
            Component.translatable(KEY + "message"), Component.translatable(KEY + "change"), () -> run(change));
    }
}
