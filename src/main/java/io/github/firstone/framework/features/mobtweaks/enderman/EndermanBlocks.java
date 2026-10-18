package io.github.firstone.framework.features.mobtweaks.enderman;

import com.mojang.serialization.Codec;
import io.github.firstone.framework.FirstOneFramework;
import io.github.firstone.framework.features.mobtweaks.MobTweaksFeature;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.EnderMan;

/**
 * Enderman Blocks Stay: an enderman picks up a block only for show, so endermen cannot spoil the world
 *
 * <p>The enderman still picks up and puts down blocks as often and in the same places as in 1.21.1, and holds the
 * block in its hands, but the world never changes: the block stays where it was, nothing is placed when it puts the
 * block down (its hands just become empty), and nothing drops when it dies (as in 1.7.10, where a held block was
 * lost). Such an enderman also despawns normally (1.21.1 keeps an enderman holding a block forever, so the block is
 * not lost; here there is nothing to lose).</p>
 *
 * <p>Only the block picked up while the option is on is shown only; the enderman keeps that mark in its save
 * ({@link #SHOWN_ONLY}). So turning the option off later never makes a shown block appear or drop, and a block picked
 * up before the option was turned on is still a real one (placed or dropped as in 1.21.1). The game events of
 * taking and placing (heard by sculk sensors) are left out too, since no block changed. Runs on the server.</p>
 */
public final class EndermanBlocks {

    /** Marks an enderman whose held block is only shown; kept in its save until it puts the block down */
    public static final AttachmentType<Boolean> SHOWN_ONLY = AttachmentRegistry.createPersistent(
        ResourceLocation.fromNamespaceAndPath(FirstOneFramework.MOD_ID, "enderman_block_shown_only"), Codec.BOOL);

    private EndermanBlocks() {}

    /**
     * Registers {@link #SHOWN_ONLY} (by loading this class) before any world is read
     *
     * <p>Called once from {@code MobTweaksFeature.initialize()}.</p>
     */
    public static void register() {
        // the attachment is registered when the class loads
    }

    /**
     * Tells whether a block an enderman picks up now is only shown (the option is on)
     *
     * @return true if the block must stay in the world
     */
    public static boolean pickUpForShow() {
        return MobTweaksFeature.getConfig().endermanBlocksStay;
    }

    /**
     * Notes that an enderman holds its block only for show (called when it picks one up with the option on)
     *
     * @param enderman the enderman
     */
    public static void markShownOnly(EnderMan enderman) {
        enderman.setAttached(SHOWN_ONLY, true);
    }

    /**
     * Tells whether an enderman's held block is only shown (so it must not be placed or dropped)
     *
     * @param enderman the enderman
     * @return true if its block is only shown
     */
    public static boolean isShownOnly(EnderMan enderman) {
        return enderman.hasAttached(SHOWN_ONLY);
    }

    /**
     * Forgets the mark when the enderman's hands become empty
     *
     * @param enderman the enderman
     */
    public static void clearShownOnly(EnderMan enderman) {
        enderman.removeAttached(SHOWN_ONLY);
    }
}
