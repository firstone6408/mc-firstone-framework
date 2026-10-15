package io.github.firstone.framework.features.packmanager.client.screen;

import io.github.firstone.framework.client.screen.Questions;
import io.github.firstone.framework.features.packmanager.PackLists;
import io.github.firstone.framework.features.packmanager.preset.PresetStore.PackPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The game's own Resource Packs screen, used to choose and order the packs of a preset in the preset form
 *
 * <p>The screen works on the game's pack list, so for the time it is open the list holds the form's packs. Done puts
 * the packs the screen shows in Selected into the form (still unsaved; the form's Save keeps them); Cancel and Escape
 * leave the form's packs as they were, asking first if something changed. Either way the game's list and the High
 * Contrast option (which follows its pack) are put back as they were: nothing is saved to {@code options.txt} and no
 * resources are loaded. Dragging and search work as in the normal screen; there is no preset button.</p>
 */
public class PresetPacksScreen extends PackSelectionScreen {

    private static final String KEY = "firstone-framework.pack_manager.edit_packs.";

    /** Screen to return to (the preset form) */
    private final Screen back;

    /** The form's preset, whose packs are chosen */
    private final PackPreset preset;

    /** The game's pack list and High Contrast option before the screen opened */
    private final List<String> gamePacks;
    private final boolean highContrast;

    /** The packs the screen showed when it opened, to tell whether Cancel throws changes away */
    private final List<String> initial;

    private PresetPacksScreen(Screen back, PackPreset preset, List<String> gamePacks, boolean highContrast) {
        super(Minecraft.getInstance().getResourcePackRepository(), repository -> { },
            Minecraft.getInstance().getResourcePackDirectory(),
            Component.translatable(KEY + "title", preset.name));
        this.back = back;
        this.preset = preset;
        this.gamePacks = gamePacks;
        this.highContrast = highContrast;
        this.initial = chosen();
    }

    /**
     * Opens the screen for the form's preset
     *
     * @param back   screen to return to (the form, which shows the packs again when it is shown)
     * @param preset the form's preset; Done replaces its pack list
     */
    public static void open(Screen back, PackPreset preset) {
        Minecraft minecraft = Minecraft.getInstance();
        PackRepository repository = minecraft.getResourcePackRepository();
        List<String> gamePacks = List.copyOf(repository.getSelectedIds());
        boolean highContrast = minecraft.options.highContrast().get();
        repository.setSelected(preset.packs.reversed()); // the game's list has the lowest pack first
        minecraft.setScreen(new PresetPacksScreen(back, preset, gamePacks, highContrast));
    }

    /** Done: the chosen packs go into the form */
    @Override
    public void onClose() {
        finish(true);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    /**
     * Goes back to the form without the changes; asks first if the packs changed
     *
     * <p>Called by the Cancel button ({@code PackScreenMixin}) and by Escape.</p>
     */
    public void cancel() {
        if (chosen().equals(this.initial)) {
            finish(false);
            return;
        }
        Questions.ask(this, Component.translatable(KEY + "discard_title"),
            Component.translatable(KEY + "discard_message"), Component.translatable(KEY + "discard"),
            Component.translatable(KEY + "keep_editing"), () -> finish(false));
    }

    /** Closes the screen, puts the game's packs back, and keeps the chosen packs in the form if asked */
    private void finish(boolean keep) {
        List<String> chosen = chosen();
        super.onClose(); // the game's own closing (stops watching the pack folder); its list is put back below
        PackRepository repository = this.minecraft.getResourcePackRepository();
        repository.setSelected(this.gamePacks);
        this.minecraft.options.highContrast().set(this.highContrast);
        if (keep && !chosen.equals(this.initial)) {
            this.preset.packs = chosen;
        }
        this.minecraft.setScreen(this.back);
    }

    /**
     * Returns the packs the screen shows in Selected, top first, with the form's missing packs (which the screen
     * cannot show) put back, each below the pack that was above it before (or on top)
     */
    private List<String> chosen() {
        // the packs the screen shows (the game's list also holds packs Fabric hides from the screen)
        List<String> result = new ArrayList<>(PackLists.selectedIds(PackLists.model(this)));
        PackRepository repository = Minecraft.getInstance().getResourcePackRepository();
        List<String> before = this.preset.packs;
        for (int index = 0; index < before.size(); index++) {
            String id = before.get(index);
            if (repository.isAvailable(id) || result.contains(id)) {
                continue;
            }
            int at = 0;
            for (int above = index - 1; above >= 0; above--) {
                int place = result.indexOf(before.get(above));
                if (place >= 0) {
                    at = place + 1;
                    break;
                }
            }
            result.add(at, id);
        }
        return result;
    }
}
