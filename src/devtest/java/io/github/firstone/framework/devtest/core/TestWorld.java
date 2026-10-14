package io.github.firstone.framework.devtest.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;

/**
 * Creates and leaves the flat test world of a feature
 *
 * <p>The world is deleted and created again on every run, so each run starts the same: creative, peaceful, cheats
 * on, always noon and clear, no mob spawning, no random ticks (ice does not melt, crops do not grow).</p>
 */
final class TestWorld {

    /** Longest time to wait for the world to load */
    private static final int LOAD_TIMEOUT_TICKS = 20 * 120;

    private TestWorld() {}

    /** Deletes the world if it exists, creates it again and waits until the player is in it */
    static void create(TestContext t, String name) {
        Minecraft minecraft = t.mc();
        t.onClientThread(() -> {
            deleteIfExists(minecraft.getLevelSource(), name);
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DOFIRETICK).set(false, null);
            rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
            rules.getRule(GameRules.RULE_SPAWN_RADIUS).set(0, null);
            LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
            minecraft.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                    .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
            return null;
        });
        t.waitUntil("the test world loads",
            () -> minecraft.level != null && minecraft.player != null && minecraft.screen == null,
            LOAD_TIMEOUT_TICKS);
        t.command("time set noon", "weather clear");
        t.ticks(20);
    }

    /** Leaves the world, like "Save and Quit to Title" */
    static void leave(TestContext t) {
        Minecraft minecraft = t.mc();
        t.onClientThread(() -> {
            if (minecraft.level != null) {
                minecraft.level.disconnect();
                minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
                minecraft.setScreen(new TitleScreen());
            }
            return null;
        });
        t.ticks(5);
    }

    private static void deleteIfExists(LevelStorageSource source, String name) {
        if (!source.levelExists(name)) {
            return;
        }
        try (LevelStorageSource.LevelStorageAccess access = source.createAccess(name)) {
            access.deleteLevel();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot delete the old test world " + name, e);
        }
    }
}
