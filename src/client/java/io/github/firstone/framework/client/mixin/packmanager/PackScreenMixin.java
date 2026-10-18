package io.github.firstone.framework.client.mixin.packmanager;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.packmanager.PackLists;
import io.github.firstone.framework.features.packmanager.PackManagerFeature;
import io.github.firstone.framework.features.packmanager.client.screen.PresetListScreen;
import io.github.firstone.framework.features.packmanager.client.screen.PresetListScreen.PresetButton;
import io.github.firstone.framework.features.packmanager.client.screen.PresetPacksScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The tool row of the pack screens: the search box (search of {@link PackLists}) and the preset button
 * ({@link PresetListScreen}), and the Cancel button of the screen that chooses a preset's packs
 *
 * <p>The row sits between the header and the two lists, which move down to make room: the search box above
 * Available, the preset button above Selected, so each keeps its place when the other is off. With both off the
 * screen is the game's own.</p>
 * <ul>
 *   <li>Search: the screen fills both lists in {@code updateList}; while a text is typed only the matching packs are
 *       passed on. The text is kept when the screen is resized or shown again. Done stays usable while the search
 *       hides every selected pack.</li>
 *   <li>Preset button (Resource Packs screen only, {@link PresetButton}): the preset the Selected list matches, or
 *       Custom; updated after every change of the lists. It opens the presets.</li>
 *   <li>{@link PresetPacksScreen}: a Cancel button before Done. The screen adds four children to its layouts in
 *       {@code init}: the title and the drag-and-drop hint, then Open Pack Folder and Done (the fourth).</li>
 * </ul>
 * <p>Also gives Pack Manager the screen's parts ({@link PackLists.Parts}).</p>
 */
@Mixin(PackSelectionScreen.class)
public abstract class PackScreenMixin extends Screen implements PackLists.Parts {

    /** Width and height of the search box and the preset button (a list's width) */
    @Unique
    private static final int TOOL_WIDTH = 200;
    @Unique
    private static final int TOOL_HEIGHT = 20;

    /** Room the tool row takes above the lists */
    @Unique
    private static final int TOOL_ROOM = 24;

    /** Width of the Cancel button */
    @Unique
    private static final int CANCEL_WIDTH = 100;

    @Unique
    private static final String KEY = "firstone-framework.pack_manager.";

    @Shadow
    @Final
    private HeaderAndFooterLayout layout;

    @Shadow
    @Final
    private PackSelectionModel model;

    @Shadow
    private TransferableSelectionList availablePackList;

    @Shadow
    private TransferableSelectionList selectedPackList;

    @Shadow
    private Button doneButton;

    @Shadow
    @Final
    private Path packDir;

    /** The typed text; kept while the screen exists */
    @Unique
    private String packManager$search = "";

    /** The search box, or null when Pack Search was off when the screen was built */
    @Unique
    @Nullable
    private EditBox packManager$searchBox;

    /** The preset button, or null when the screen has none */
    @Unique
    @Nullable
    private PresetButton packManager$presetButton;

    private PackScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    private void populateLists() {
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", ordinal = 3, target =
        "Lnet/minecraft/client/gui/layouts/LinearLayout;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement packManager$addCancelButton(LinearLayout footer, LayoutElement done,
                                                     Operation<LayoutElement> original) {
        if ((Object) this instanceof PresetPacksScreen editor) {
            footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> editor.cancel())
                .width(CANCEL_WIDTH).build());
        }
        return original.call(footer, done);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void packManager$addTools(CallbackInfo ci) {
        this.packManager$searchBox = null;
        this.packManager$presetButton = null;
        if (PackManagerFeature.getConfig().packSearch) {
            EditBox box = new EditBox(this.font, TOOL_WIDTH, TOOL_HEIGHT, Component.translatable(KEY + "search"));
            box.setHint(Component.translatable(KEY + "search.hint").withStyle(ChatFormatting.DARK_GRAY));
            box.setValue(this.packManager$search);
            box.setResponder(text -> {
                this.packManager$search = text;
                populateLists();
            });
            this.packManager$searchBox = addRenderableWidget(box);
        } else {
            this.packManager$search = "";
        }
        PackSelectionScreen self = (PackSelectionScreen) (Object) this;
        if (PackManagerFeature.getConfig().presets && PresetListScreen.isOffered(self)) {
            this.packManager$presetButton = addRenderableWidget(new PresetButton(this.model, TOOL_WIDTH, TOOL_HEIGHT,
                button -> this.minecraft.setScreen(new PresetListScreen(self))));
        }
        packManager$placeTools();
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void packManager$placeToolsAfterLayout(CallbackInfo ci) {
        packManager$placeTools();
    }

    /** Puts the tools under the header, each above its list, and moves both lists down below them */
    @Unique
    private void packManager$placeTools() {
        if (this.packManager$searchBox == null && this.packManager$presetButton == null) {
            return;
        }
        for (TransferableSelectionList list : new TransferableSelectionList[] {
            this.availablePackList, this.selectedPackList}) {
            int x = list.getX();
            list.updateSizeAndPosition(list.getWidth(), list.getHeight() - TOOL_ROOM, list.getY() + TOOL_ROOM);
            list.setX(x);
        }
        int y = this.layout.getHeaderHeight() + 1;
        if (this.packManager$searchBox != null) {
            this.packManager$searchBox.setPosition(this.availablePackList.getX(), y);
        }
        if (this.packManager$presetButton != null) {
            this.packManager$presetButton.setPosition(this.selectedPackList.getX(), y);
        }
    }

    @ModifyVariable(method = "updateList", at = @At("HEAD"), argsOnly = true)
    private Stream<PackSelectionModel.Entry> packManager$filter(Stream<PackSelectionModel.Entry> entries) {
        String text = this.packManager$search;
        return PackLists.isSearchActive(text) ? entries.filter(entry -> PackLists.matchesSearch(entry, text)) : entries;
    }

    @Inject(method = "populateLists", at = @At("TAIL"))
    private void packManager$afterListsChanged(CallbackInfo ci) {
        // the game greys Done out when the Selected list shows nothing, which the search may cause
        if (PackLists.isSearchActive(this.packManager$search)) {
            this.doneButton.active = this.model.getSelected().findAny().isPresent();
        }
        if (this.packManager$presetButton != null) {
            this.packManager$presetButton.update();
        }
    }

    @Override
    public PackSelectionModel packManager$model() {
        return this.model;
    }

    @Override
    public TransferableSelectionList packManager$availableList() {
        return this.availablePackList;
    }

    @Override
    public TransferableSelectionList packManager$selectedList() {
        return this.selectedPackList;
    }

    @Override
    public Path packManager$packDir() {
        return this.packDir;
    }

    @Override
    public boolean packManager$isSearching() {
        return this.packManager$searchBox != null && PackLists.isSearchActive(this.packManager$search);
    }

    @Override
    public void packManager$clearSearch() {
        if (this.packManager$searchBox != null) {
            this.packManager$searchBox.setValue("");
        }
        this.packManager$search = "";
    }
}
