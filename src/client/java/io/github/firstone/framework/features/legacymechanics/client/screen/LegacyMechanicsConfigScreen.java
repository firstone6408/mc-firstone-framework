package io.github.firstone.framework.features.legacymechanics.client.screen;

import io.github.firstone.framework.client.screen.CategoryMenuScreen;
import io.github.firstone.framework.client.screen.ConfigCategory;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import io.github.firstone.framework.features.legacymechanics.enchanting.LegacyEnchanting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;

import static io.github.firstone.framework.client.screen.ConfigCategory.slider;
import static io.github.firstone.framework.client.screen.ConfigCategory.toggle;

/**
 * Settings screen of the Legacy Mechanics feature: its rules in categories
 *
 * <ul>
 *   <li><b>Combat</b> — knockback and sword blocking</li>
 *   <li><b>Players</b> — the crouching hitbox and natural regeneration</li>
 *   <li><b>Drops</b> — item drops, experience orbs and items thrown when a screen closes</li>
 *   <li><b>Boats</b> — boat breaking and coasting</li>
 *   <li><b>Enchanting</b> — the enchanting table's cost, rolls and paid clues</li>
 * </ul>
 *
 * <p>These are server rules: changes apply to singleplayer right away; a dedicated server uses its own
 * {@code legacy_mechanics.json}, which the notice on top of the menu explains.</p>
 */
public class LegacyMechanicsConfigScreen extends CategoryMenuScreen<LegacyMechanicsConfig> {

    /** Name of the feature's config file, for the server notice */
    private static final String CONFIG_FILE = "legacy_mechanics.json";

    /** The categories and their options, in the order they are shown */
    private static final List<ConfigCategory<LegacyMechanicsConfig>> CATEGORIES = List.of(
        ConfigCategory.of("combat", Items.IRON_SWORD, List.of(
            toggle("legacy_knockback", c -> c.legacyKnockback, (c, v) -> c.legacyKnockback = v),
            toggle("legacy_attack_knockback", c -> c.legacyAttackKnockback, (c, v) -> c.legacyAttackKnockback = v),
            toggle("legacy_sword_blocking", c -> c.legacySwordBlocking, (c, v) -> c.legacySwordBlocking = v))),
        ConfigCategory.of("players", Items.GOLDEN_APPLE, List.of(
            toggle("legacy_crouch_hitbox", c -> c.legacyCrouchHitbox, (c, v) -> c.legacyCrouchHitbox = v),
            toggle("legacy_regeneration", c -> c.legacyRegeneration, (c, v) -> c.legacyRegeneration = v))),
        ConfigCategory.of("drops", Items.EXPERIENCE_BOTTLE, List.of(
            toggle("legacy_item_drops", c -> c.legacyItemDrops, (c, v) -> c.legacyItemDrops = v),
            toggle("legacy_experience", c -> c.legacyExperience, (c, v) -> c.legacyExperience = v),
            toggle("drop_items_on_close", c -> c.dropItemsOnClose, (c, v) -> c.dropItemsOnClose = v))),
        ConfigCategory.of("boats", Items.OAK_BOAT, List.of(
            toggle("legacy_boat_breaking", c -> c.legacyBoatBreaking, (c, v) -> c.legacyBoatBreaking = v),
            toggle("break_chest_boats_and_rafts", c -> c.breakChestBoatsAndRafts,
                (c, v) -> c.breakChestBoatsAndRafts = v, c -> c.legacyBoatBreaking),
            toggle("boats_safe_on_ice", c -> c.boatsSafeOnIce, (c, v) -> c.boatsSafeOnIce = v,
                c -> c.legacyBoatBreaking),
            toggle("legacy_boat_coasting", c -> c.legacyBoatCoasting, (c, v) -> c.legacyBoatCoasting = v))),
        ConfigCategory.of("enchanting", Items.ENCHANTING_TABLE, List.of(
            toggle("legacy_enchant_cost", c -> c.legacyEnchantCost, (c, v) -> c.legacyEnchantCost = v),
            toggle("legacy_enchant_reroll", c -> c.legacyEnchantReroll, (c, v) -> c.legacyEnchantReroll = v),
            toggle("paid_enchant_clues", c -> c.paidEnchantClues, (c, v) -> c.paidEnchantClues = v,
                c -> c.legacyEnchantReroll),
            slider("enchant_clue_lapis", LegacyEnchanting.MIN_CLUE_LAPIS, LegacyEnchanting.MAX_CLUE_LAPIS, 1,
                c -> c.enchantClueLapis, (c, v) -> c.enchantClueLapis = (int) v,
                LegacyMechanicsConfigScreen::lapis, c -> c.legacyEnchantReroll && c.paidEnchantClues),
            slider("enchant_clue_levels", LegacyEnchanting.MIN_CLUE_LEVELS, LegacyEnchanting.MAX_CLUE_LEVELS, 1,
                c -> c.enchantClueLevels, (c, v) -> c.enchantClueLevels = (int) v,
                LegacyMechanicsConfigScreen::levels, c -> c.legacyEnchantReroll && c.paidEnchantClues))));

    /**
     * Creates the Legacy Mechanics settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public LegacyMechanicsConfigScreen(Screen parent) {
        super(parent, "firstone-framework.legacy_mechanics.", LegacyMechanicsFeature::getConfig,
            LegacyMechanicsConfig::new, LegacyMechanicsFeature::saveConfig, LegacyMechanicsFeature::resetConfig,
            CONFIG_FILE, CATEGORIES);
    }

    /** The text of the clue lapis slider: "3 Lapis Lazuli" */
    private static Component lapis(double value) {
        return Component.translatable("firstone-framework.legacy_mechanics.lapis_value", (int) value);
    }

    /** The text of the clue levels slider: "3 Levels" */
    private static Component levels(double value) {
        return Component.translatable("firstone-framework.legacy_mechanics.levels_value", (int) value);
    }
}
