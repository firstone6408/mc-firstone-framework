package io.github.firstone.framework.features.legacyhud;

import io.github.firstone.framework.common.enchanting.EnchantingRulesPayload;
import io.github.firstone.framework.features.legacymechanics.enchanting.LegacyEnchanting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.jetbrains.annotations.Nullable;

/**
 * The enchanting table screen of Legacy Mechanics' Legacy Enchanting: the Show Clues button and the levels a line
 * takes
 *
 * <p>The server decides everything ({@link LegacyEnchanting}); it tells a client with the mod its rules
 * ({@link EnchantingRulesPayload}). Every connection starts without rules, so on a server without the mod the
 * enchanting table stays as in 1.21.1. With Paid Clues on, the table gets a "?" button left of it: it asks the
 * server to show the clues through the game's own button packet; the server checks the price again and takes it.
 * With Legacy Enchant Cost on, the tooltip of a line says the real number of levels taken.</p>
 */
public final class EnchantingClues {

    /** Size of the Show Clues button and its gap to the table's left edge */
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_GAP = 2;

    /** Top of the first line of the table, from the top of the screen's image ({@code EnchantmentScreen}) */
    private static final int FIRST_LINE_TOP = 14;

    /** The game's text keys of the levels a line takes */
    private static final String LEVEL_ONE = "container.enchant.level.one";
    private static final String LEVEL_MANY = "container.enchant.level.many";

    /** The values of a text without values */
    private static final Object[] NO_ARGS = new Object[0];

    /** Prefix of this part's text keys */
    private static final String KEY = "firstone-framework.legacy_hud.enchant_clues.";

    /** What the server said, or null if it said nothing (no mod); written by the network events */
    @Nullable
    private static volatile EnchantingRulesPayload rules;

    private EnchantingClues() {}

    /**
     * Listens to the server's enchanting rules and forgets them between connections
     *
     * <p>Called once by {@link LegacyHudFeature#initializeClient()}. Always on: what the server rules is not a
     * setting of the client.</p>
     */
    public static void register() {
        ClientPlayConnectionEvents.INIT.register((handler, client) -> rules = null);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> rules = null);
        ClientPlayNetworking.registerGlobalReceiver(EnchantingRulesPayload.TYPE,
            (payload, context) -> rules = payload);
    }

    /**
     * Makes the Show Clues button for an enchanting table screen, if the server has paid clues
     *
     * <p>Called when the screen is set up.</p>
     *
     * @param left left edge of the screen's image
     * @param top  top edge of the screen's image
     * @param menu the table's screen, whose number the button packet carries
     * @return the button, or null when the server has no paid clues
     */
    @Nullable
    public static Button button(int left, int top, EnchantmentMenu menu) {
        EnchantingRulesPayload current = rules;
        if (current == null || !current.paidClues()) {
            return null;
        }
        Button button = Button.builder(Component.literal("?"), pressed -> press(menu))
            .bounds(left - BUTTON_SIZE - BUTTON_GAP, top + FIRST_LINE_TOP, BUTTON_SIZE, BUTTON_SIZE)
            .build();
        update(button, menu, -1);
        return button;
    }

    /** Asks the server to show the clues */
    private static void press(EnchantmentMenu menu) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LegacyEnchanting.CLUE_BUTTON);
        }
    }

    /**
     * Grays the button out while the clues cannot be paid for, and tells why in its tooltip
     *
     * <p>Called every tick while the screen is open. The server checks the same again when the button is pressed.
     * The button hides when the server turns paid clues off while the screen is open. The tooltip is made again
     * only when what it says changes.</p>
     *
     * @param button    the Show Clues button
     * @param menu      the table's screen
     * @param lastState what this returned last time, or -1 the first time
     * @return a number that changes whenever the tooltip has to change
     */
    public static int update(Button button, EnchantmentMenu menu, int lastState) {
        EnchantingRulesPayload current = rules;
        Player player = Minecraft.getInstance().player;
        if (current == null || player == null || !current.paidClues()) {
            button.visible = false;
            button.active = false;
            return lastState;
        }
        button.visible = true;
        int lapisNeeded = current.clueLapis() + LegacyEnchanting.LAPIS_FOR_ENCHANTING;
        boolean free = player.hasInfiniteMaterials();
        boolean hasRoll = LegacyEnchanting.hasRoll(menu.costs);
        boolean enoughLapis = free || menu.getGoldCount() >= lapisNeeded;
        boolean enoughLevels = free || player.experienceLevel >= current.clueLevels();
        boolean shown = cluesShown(menu);
        button.active = hasRoll && enoughLapis && enoughLevels;

        int state = (hasRoll ? 1 : 0) | (enoughLapis ? 2 : 0) | (enoughLevels ? 4 : 0) | (shown ? 8 : 0)
            | (free ? 16 : 0) | current.clueLapis() << 5 | current.clueLevels() << 10;
        if (state != lastState) {
            button.setTooltip(Tooltip.create(tooltip(current, free, hasRoll, enoughLapis, enoughLevels, shown)));
        }
        return state;
    }

    /** The tooltip of the button: what it does, its price, and what is missing */
    private static Component tooltip(EnchantingRulesPayload current, boolean free, boolean hasRoll,
                                     boolean enoughLapis, boolean enoughLevels, boolean shown) {
        MutableComponent text = Component.translatable(KEY + "title").append("\n")
            .append(price(current).withStyle(ChatFormatting.GRAY));
        if (shown) {
            text.append("\n").append(Component.translatable(KEY + "again").withStyle(ChatFormatting.GRAY));
        }
        if (free) {
            text.append("\n").append(Component.translatable(KEY + "free").withStyle(ChatFormatting.GRAY));
        }
        if (!hasRoll) {
            text.append("\n").append(Component.translatable(KEY + "needs_item").withStyle(ChatFormatting.RED));
        }
        if (!enoughLapis) {
            text.append("\n").append(Component.translatable(KEY + "needs_lapis",
                current.clueLapis() + LegacyEnchanting.LAPIS_FOR_ENCHANTING).withStyle(ChatFormatting.RED));
        }
        if (!enoughLevels) {
            text.append("\n").append(Component.translatable(KEY + "needs_levels", current.clueLevels())
                .withStyle(ChatFormatting.RED));
        }
        return text;
    }

    /** "Costs 3 Lapis Lazuli and 3 Levels", or only the lapis lazuli when the clues take no levels */
    private static MutableComponent price(EnchantingRulesPayload current) {
        if (current.clueLevels() == 0) {
            return Component.translatable(KEY + "price_lapis", current.clueLapis());
        }
        return Component.translatable(KEY + "price", current.clueLapis(), current.clueLevels());
    }

    /** Whether the server sent the clues of the current roll */
    private static boolean cluesShown(EnchantmentMenu menu) {
        for (int clue : menu.enchantClue) {
            if (clue >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the text of the levels a line takes, for a text without values (the levels of line 1)
     *
     * @param key      the text's key
     * @param original the text the game made
     * @param menu     the table's screen
     * @return the text to show
     * @see #levelsText(String, Object[], MutableComponent, EnchantmentMenu)
     */
    public static MutableComponent levelsText(String key, MutableComponent original, EnchantmentMenu menu) {
        return levelsText(key, NO_ARGS, original, menu);
    }

    /**
     * Returns the text of the levels a line takes, with the real number when the server takes the levels on the
     * line
     *
     * <p>Called for each text the enchanting table's tooltip makes; texts other than the levels taken are returned
     * as they are. The game names the line by the levels 1.21.1 takes (1 to 3).</p>
     *
     * @param key      the text's key
     * @param args     the text's values (for the levels of lines 2 and 3: the line's number)
     * @param original the text the game made
     * @param menu     the table's screen
     * @return the text to show
     */
    public static MutableComponent levelsText(String key, Object[] args, MutableComponent original,
                                              EnchantmentMenu menu) {
        EnchantingRulesPayload current = rules;
        int line = LEVEL_ONE.equals(key) ? 0
            : LEVEL_MANY.equals(key) && args.length == 1 && args[0] instanceof Integer number ? number - 1 : -1;
        if (current == null || !current.fullCost() || line < 0 || line >= menu.costs.length) {
            return original;
        }
        int cost = menu.costs[line];
        return cost == 1 ? Component.translatable(LEVEL_ONE) : Component.translatable(LEVEL_MANY, cost);
    }
}
