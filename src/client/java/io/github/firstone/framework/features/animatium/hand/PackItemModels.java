package io.github.firstone.framework.features.animatium.hand;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.firstone.framework.FirstOneFramework;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tells which item models a resource pack or another mod changes, so "Resource Pack Items" can keep their look
 *
 * <p>An item's look in the hand comes from its model file ({@code models/item/<name>.json}) and the parents it
 * inherits from ({@code item/handheld}, {@code item/generated}…); packs often change those parents to move every
 * tool at once. An item counts as changed when its model or any parent comes from somewhere else than the game
 * itself. The answers are kept until the next resource reload (render thread only).</p>
 */
public final class PackItemModels {

    /** Source pack id of the game's own resources */
    private static final String GAME_PACK = "vanilla";

    /** Most parents followed; real models have a handful */
    private static final int MAX_PARENTS = 16;

    /** Answers per item, cleared on every resource reload */
    private static final Map<Item, Boolean> CHANGED = new HashMap<>();

    private PackItemModels() {}

    /**
     * Makes the checks start over after every resource reload
     *
     * <p>Called once by {@code AnimatiumFeature.initializeClient()}.</p>
     */
    public static void registerReloadListener() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override
                public ResourceLocation getFabricId() {
                    return ResourceLocation.fromNamespaceAndPath(FirstOneFramework.MOD_ID, "pack_item_models");
                }

                @Override
                public void onResourceManagerReload(ResourceManager resourceManager) {
                    CHANGED.clear();
                }
            });
    }

    /**
     * Tells whether a resource pack or another mod gives this item's model or one of its parents
     *
     * @param item the item
     * @return true if its look in the hand is not the game's own
     */
    public static boolean changedByPack(Item item) {
        return CHANGED.computeIfAbsent(item, PackItemModels::check);
    }

    private static boolean check(Item item) {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        ResourceLocation model = BuiltInRegistries.ITEM.getKey(item).withPrefix("item/");
        for (int i = 0; i < MAX_PARENTS && model != null; i++) {
            Optional<Resource> resource = resources.getResource(model.withPath(path -> "models/" + path + ".json"));
            if (resource.isEmpty()) {
                // a built-in model ("builtin/generated", "builtin/entity") or a missing one
                return false;
            }
            if (!GAME_PACK.equals(resource.get().sourcePackId())) {
                return true;
            }
            model = parent(resource.get());
        }
        return false;
    }

    /** Returns the model a model file inherits from, or null when it has none or cannot be read */
    private static ResourceLocation parent(Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.has("parent") ? ResourceLocation.tryParse(json.get("parent").getAsString()) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
