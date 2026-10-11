package io.github.firstone.framework.features.legacymechanics.sword;

import io.github.firstone.framework.common.sword.SwordBlockingPayload;
import io.github.firstone.framework.common.sword.SwordBlockingSupport;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.UseAnim;

/**
 * 1.7.10 sword blocking rules
 *
 * <p>1.7.10 {@code ItemSword}: a right click starts using the sword ({@code EnumAction.block}, 72000 ticks) until the
 * button is let go; {@code EntityPlayer.damageEntity} then turns damage {@code d} into {@code (1 + d) × 0.5} before
 * armor, except damage that ignores armor (fire, falling, drowning, magic…). Using an item also slows walking and
 * stops sprinting, which the game already does for every item in use. 1.21.1 swords cannot be used at all.</p>
 *
 * <p>Both sides must agree: the server follows its "Legacy Sword Blocking" setting and tells each client
 * ({@link SwordBlockingPayload}); a client only blocks when its server said so. A blocking sword is not a shield: the
 * shield rules (blocking everything from the front, disabled by axes) are left out for swords. 1.7.10 had no off
 * hand: an item there with its own use (shield, food, bow…) or a block to place is used instead of blocking, as
 * 1.21.1 would.</p>
 */
public final class LegacySwordBlocking {

    /** How long a sword can be used (1.7.10 {@code getMaxItemUseDuration}) */
    public static final int USE_DURATION = 72000;

    /** The setting last sent to the players, to notice when it changes */
    private static boolean lastSent;

    private LegacySwordBlocking() {}

    /**
     * Sends the setting to each player who joins and again to everyone when it changes
     *
     * <p>Called once by {@link LegacyMechanicsFeature#initialize()}.</p>
     */
    public static void registerSync() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer(), enabled()));
        ServerTickEvents.END_SERVER_TICK.register(LegacySwordBlocking::sendIfChanged);
    }

    private static void sendIfChanged(MinecraftServer server) {
        boolean enabled = enabled();
        if (enabled != lastSent) {
            lastSent = enabled;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                send(player, enabled);
            }
        }
    }

    private static void send(ServerPlayer player, boolean enabled) {
        lastSent = enabled;
        if (ServerPlayNetworking.canSend(player, SwordBlockingPayload.TYPE)) {
            ServerPlayNetworking.send(player, new SwordBlockingPayload(enabled));
        }
    }

    /** The server's setting */
    private static boolean enabled() {
        return LegacyMechanicsFeature.getConfig().legacySwordBlocking;
    }

    /**
     * Tells whether swords block on one side of the game
     *
     * @param clientSide true on the client (follows what the server said), false on the server (its setting)
     * @return true if swords block there
     */
    public static boolean enabled(boolean clientSide) {
        return clientSide ? SwordBlockingSupport.serverAllows() : enabled();
    }

    /**
     * Tells whether swords block on either side, for questions asked without knowing the side (the use animation
     * and duration of a sword, which only matter while it is used)
     *
     * @return true if the server's setting or what the client was told allows it
     */
    public static boolean enabledAnywhere() {
        return SwordBlockingSupport.serverAllows() || enabled();
    }

    /**
     * Tells whether an item is a sword
     *
     * @param stack any item
     * @return true for swords
     */
    public static boolean isSword(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }

    /**
     * Tells whether a right click with this hand starts blocking with a sword
     *
     * @param clientSide true on the client
     * @param player     the player
     * @param hand       the hand used
     * @return true for a sword in the main hand while the off hand has nothing of its own to use
     */
    public static boolean blocks(boolean clientSide, Player player, InteractionHand hand) {
        return enabled(clientSide) && hand == InteractionHand.MAIN_HAND && isSword(player.getMainHandItem())
            && offHandFree(player.getOffhandItem());
    }

    /** True if the off hand has nothing that 1.21.1 would use instead (an item with a use, or a block) */
    private static boolean offHandFree(ItemStack offHand) {
        return offHand.isEmpty() || isSword(offHand)
            || offHand.getUseAnimation() == UseAnim.NONE && !(offHand.getItem() instanceof BlockItem);
    }

    /**
     * Tells whether a player is blocking with a sword (on the server)
     *
     * @param player the player
     * @return true while a sword is used and the setting is on
     */
    public static boolean isBlocking(Player player) {
        return enabled() && player.isUsingItem() && isSword(player.getUseItem());
    }

    /**
     * Returns the damage left after a sword block, before armor (1.7.10 {@code (1 + d) × 0.5})
     *
     * @param player the player taking the damage
     * @param source what hurts the player
     * @param damage the damage before armor
     * @return the damage after the block, or {@code damage} when not blocking or the damage ignores armor
     */
    public static float blockedDamage(Player player, DamageSource source, float damage) {
        if (damage > 0.0F && !source.is(DamageTypeTags.BYPASSES_ARMOR) && isBlocking(player)) {
            return (1.0F + damage) * 0.5F;
        }
        return damage;
    }
}
