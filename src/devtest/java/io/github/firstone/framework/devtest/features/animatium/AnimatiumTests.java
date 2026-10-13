package io.github.firstone.framework.devtest.features.animatium;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.firstone.framework.devtest.core.CategoryMenuChecks;
import io.github.firstone.framework.devtest.core.FeatureTests;
import io.github.firstone.framework.devtest.core.Images;
import io.github.firstone.framework.devtest.core.SoundRecorder;
import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestSite;
import io.github.firstone.framework.devtest.features.animatium.camera.HurtTiltTests;
import io.github.firstone.framework.devtest.features.animatium.entity.MobLandingTests;
import io.github.firstone.framework.devtest.features.animatium.hand.FirstPersonItemTests;
import io.github.firstone.framework.devtest.features.animatium.item.GroundItemTests;
import io.github.firstone.framework.devtest.features.animatium.particle.FallParticlesTests;
import io.github.firstone.framework.devtest.features.animatium.model.ThirdPersonItemTests;
import io.github.firstone.framework.devtest.mixin.HandRendererAccessor;
import io.github.firstone.framework.devtest.mixin.MobAccessor;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import io.github.firstone.framework.features.combattweaks.CombatTweaksFeature;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.particle.AttackSweepParticle;
import net.minecraft.client.particle.CritParticle;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tests of Animatium: the 1.7.10 animations and feel on the client
 *
 * <p>Most checks are exact client values: eye height, hand height of the first-person item, positions and angles
 * other entities are drawn at, body yaw (against the 1.7.10 formula {@code EntityLivingBase.func_110146_f}, written
 * here again), and the model's parts after a frame. Particles and sounds come from real hits. The hurt tint is
 * compared in screenshots.</p>
 */
public final class AnimatiumTests implements FeatureTests {

    /** Eye height of a standing player */
    private static final double STANDING_EYES = 1.62;

    @Override
    public String id() {
        return "animatium";
    }

    @Override
    public List<Object> configs() {
        // Combat Tweaks decides the attack charge and the sweep, which some checks use
        return List.of(AnimatiumFeature.getConfig(), CombatTweaksFeature.getConfig());
    }

    @Override
    public List<TestCase> tests() {
        List<TestCase> tests = new ArrayList<>(List.of(
            // sneak
            TestCase.of("Settings: every option in one category of 2 to 7, each opens, writes and resets on its own",
                this::settings),
            TestCase.of("Old Sneak: the eyes drop 0.08 with the 1.7.10 timing; off: 1.21.1 crouch", this::sneakEyes),
            TestCase.of("Old Sneak: the hitbox keeps 1.8 blocks; camera and aim use the same eyes", this::sneakHitbox),
            // first-person hand
            TestCase.of("Item Drop: no arm swing when dropping; off: swing", this::dropSwing),
            TestCase.of("Item Drop: dropping the last item lowers it before the empty hand rises", this::dropLastItem),
            TestCase.of("Item Drop: no arm swing throwing an item out of the inventory (outside the window, Ctrl+Q, "
                + "creative); the item is still thrown; off: swing", this::screenDropSwing),
            TestCase.of("Re-equip: no dip after an attack; off: dip (attack cooldown)", this::reequipAttack),
            TestCase.of("Re-equip: no re-equip when only durability changes in the same slot", this::reequipDurability),
            TestCase.of("Re-equip: mining a block does not lower and raise the pickaxe; off: it does",
                this::reequipMining),
            TestCase.of("Re-equip: swinging a sword in the air or hitting a mob keeps it up; off: it dips",
                this::reequipSword),
            TestCase.of("Item Use: no arm swing using an item in the air; off: swing", this::useSwing),
            TestCase.of("Item Use: the hand dips only when the used stack changed", this::useDip),
            TestCase.of("Item Use: the server's echo of the player's own swing is ignored", this::serverSwingEcho),
            TestCase.of("Mob Swing: empty-handed mobs do not swing; with an item they do", this::mobSwing),
            // other entities
            TestCase.of("Entity Sync: positions in 1/32 block, moves under 0.125 ignored; off: exact", this::syncMove),
            TestCase.of("Entity Sync: turns under 5.6 degrees ignored, angles in 1/256 turns", this::syncRotation),
            TestCase.of("Entity Sync: heads turn at once; off: over a few ticks", this::syncHead),
            TestCase.of("Mob Physics: the client moves mobs with their velocity; off: it holds it", this::mobPhysics),
            TestCase.of("Body Rotation: the player's body follows the head like 1.7.10 (75 degrees)",
                this::bodyRotation),
            TestCase.of("Body Rotation: no mob's head turns further from its body than 75 degrees or than in 1.21.1",
                this::mobHeadLimit),
            TestCase.of("Body Rotation: walking mobs face the way they walk, also while looking at the player",
                this::walkingBody),
            // models
            TestCase.of("Sneak Pose: the 1.7.10 model while sneaking, also when flying; off: 1.21.1", this::sneakPose),
            TestCase.of("Zombie Arms: zombies and skeletons hold their arms straight forward", this::zombieArms),
            TestCase.of("Hurt Tint: hurt mobs get the 1.7.10 red; off: the 1.21.1 tint", this::hurtTint),
            TestCase.of("Hurt Tint: a skull worn and a sword held by a hurt mob keep their colors",
                this::hurtTintExcludes),
            // 1.9 combat effects
            TestCase.of("Combat: no sweep particle, damage indicators or attack sounds; off: all of them",
                this::combatEffects)
        ));
        // parts with their own test classes, in the folders of the feature's parts
        tests.addAll(HurtTiltTests.tests());
        tests.addAll(FirstPersonItemTests.tests());
        tests.addAll(ThirdPersonItemTests.tests());
        tests.addAll(GroundItemTests.tests());
        tests.addAll(FallParticlesTests.tests());
        tests.addAll(MobLandingTests.tests());
        return tests;
    }
    private void settings(TestContext t) {
        CategoryMenuChecks.check(t, "animatium", "firstone-framework.animatium.", AnimatiumFeature::getConfig,
            AnimatiumConfig::new, "animatium.json");
    }


    private static AnimatiumConfig config() {
        return AnimatiumFeature.getConfig();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    // ---- sneak ----------------------------------------------------------------------------------------------------

    /** Stands on the floor (creative, not flying), sneaks for 5 ticks and returns the eye heights tick by tick */
    private static List<Double> sneakAndRelease(TestContext t) {
        t.teleport(4.5, 0.0, 4.5, 0.0F, 0.0F);
        t.setFlying(false);
        t.ticks(10);
        List<Double> eyes = new ArrayList<>();
        t.hold(t.mc().options.keyShift);
        for (int i = 0; i < 5; i++) {
            t.tick();
            eyes.add((double) t.player().getEyeHeight());
        }
        t.release(t.mc().options.keyShift);
        for (int i = 0; i < 5; i++) {
            t.tick();
            eyes.add((double) t.player().getEyeHeight());
        }
        t.note("eye heights", eyes.stream().map(value -> format(value)).toList());
        return eyes;
    }

    private void sneakEyes(TestContext t) {
        List<Double> on = sneakAndRelease(t);
        // 1.7.10: yOffset2 = 0.2 while sneaking, × 0.4 every tick
        t.check("sneaking: eyes 1.62 - 0.08", on.get(4)).near(STANDING_EYES - 0.08, 1.0E-5);
        t.check("1 tick after letting go: 1.62 - 0.032", on.get(5)).near(STANDING_EYES - 0.032, 1.0E-5);
        t.check("2 ticks after: 1.62 - 0.0128", on.get(6)).near(STANDING_EYES - 0.0128, 1.0E-5);
        config().oldSneak = false;
        List<Double> off = sneakAndRelease(t);
        t.check("off: sneaking eyes (1.21.1 crouch)", off.get(4)).near(1.27, 1.0E-5);
        t.check("off: 5 ticks after letting go", off.get(9)).near(STANDING_EYES, 1.0E-5);
    }

    private void sneakHitbox(TestContext t) {
        t.teleport(4.5, 0.0, 4.5, 0.0F, 0.0F);
        t.setFlying(false);
        t.ticks(10);
        t.hold(t.mc().options.keyShift);
        t.ticks(5);
        LocalPlayer player = t.player();
        t.check("crouching", player.isCrouching()).isTrue();
        t.check("client hitbox height", (double) player.getBbHeight()).near(1.8, 1.0E-5);
        t.check("aim: eye position height", player.getEyePosition(1.0F).y - player.getY())
            .near(STANDING_EYES - 0.08, 1.0E-5);
        t.ticks(2);
        double camera = t.mc().gameRenderer.getMainCamera().getPosition().y - player.getY();
        t.check("camera height", camera).near(STANDING_EYES - 0.08, 1.0E-3);
        t.release(t.mc().options.keyShift);
        t.ticks(10);
        config().oldSneak = false;
        t.hold(t.mc().options.keyShift);
        t.ticks(5);
        t.check("off: client hitbox height", (double) t.player().getBbHeight()).near(1.5, 1.0E-5);
    }

    // ---- first-person hand ----------------------------------------------------------------------------------------

    private static HandRendererAccessor hand(TestContext t) {
        return (HandRendererAccessor) t.mc().gameRenderer.itemInHandRenderer;
    }

    /** Lowest hand height now and over the next ticks */
    private static double lowestHand(TestContext t, int ticks) {
        double lowest = hand(t).devtest$getMainHandHeight();
        for (int i = 0; i < ticks; i++) {
            t.tick();
            lowest = Math.min(lowest, hand(t).devtest$getMainHandHeight());
        }
        return lowest;
    }

    /** Whether the local player swings its arm within a few ticks after an action */
    private static boolean swingsAfter(TestContext t, Runnable action) {
        t.player().swinging = false;
        action.run();
        boolean swung = false;
        for (int i = 0; i < 3; i++) {
            swung |= t.player().swinging;
            t.tick();
        }
        return swung;
    }

    private void dropSwing(TestContext t) {
        t.holdItem(new ItemStack(Items.STICK, 16));
        t.ticks(10);
        int key = t.mc().options.keyDrop.getDefaultKey().getValue();
        t.check("on: arm swings when dropping", swingsAfter(t, () -> t.pressKey(key))).isFalse();
        config().oldItemDrop = false;
        t.check("off: arm swings when dropping", swingsAfter(t, () -> t.pressKey(key))).isTrue();
    }

    /** Item entities the server has around the player */
    private static int thrownItems(TestContext t) {
        return t.onServer(level -> level.getEntitiesOfClass(ItemEntity.class,
            t.player().getBoundingBox().inflate(8.0)).size());
    }

    /**
     * Throws sticks out of the open inventory as a player clicks: picked up and dropped outside the window, then
     * Ctrl+Q over a slot, then a creative throw; returns {swung after each, items thrown}
     */
    private static boolean[] throwOutOfInventory(TestContext t) {
        // slot 9 of the inventory screen is the first slot of the main inventory
        t.withServerPlayer(player -> player.getInventory().setItem(9, new ItemStack(Items.STICK, 16)));
        t.ticks(5);
        t.openScreen(() -> new InventoryScreen(t.player()));
        t.ticks(5);
        int before = thrownItems(t);
        boolean outside = swingsAfter(t, () -> t.onClientThread(() -> {
            int id = t.player().inventoryMenu.containerId;
            t.mc().gameMode.handleInventoryMouseClick(id, 9, 0, ClickType.PICKUP, t.player());
            t.mc().gameMode.handleInventoryMouseClick(id, -999, 1, ClickType.PICKUP, t.player());
            return null;
        }));
        boolean ctrlQ = swingsAfter(t, () -> t.onClientThread(() -> {
            int id = t.player().inventoryMenu.containerId;
            t.mc().gameMode.handleInventoryMouseClick(id, -999, 0, ClickType.PICKUP, t.player());
            t.withServerPlayer(player -> player.getInventory().setItem(9, new ItemStack(Items.STICK, 16)));
            return null;
        }));
        t.ticks(5);
        boolean throwSlot = swingsAfter(t, () -> t.onClientThread(() -> {
            t.mc().gameMode.handleInventoryMouseClick(t.player().inventoryMenu.containerId, 9, 1, ClickType.THROW,
                t.player());
            return null;
        }));
        t.closeScreen();
        // the creative inventory throws with the same call
        boolean creative = swingsAfter(t, () -> t.onClientThread(() -> t.player().drop(new ItemStack(Items.STICK),
            true)));
        t.ticks(5);
        boolean thrown = thrownItems(t) > before;
        t.removeEntities();
        t.withServerPlayer(player -> player.getInventory().clearContent());
        return new boolean[] {outside, ctrlQ, throwSlot, creative, thrown};
    }

    private void screenDropSwing(TestContext t) {
        t.gameMode(GameType.SURVIVAL);
        boolean[] on = throwOutOfInventory(t);
        t.check("on: arm swings throwing one stick outside the window", on[0]).isFalse();
        t.check("on: arm swings throwing the rest outside the window", on[1]).isFalse();
        t.check("on: arm swings throwing a stack with Ctrl+Q over a slot", on[2]).isFalse();
        t.check("on: arm swings throwing out of the creative inventory", on[3]).isFalse();
        t.check("on: the sticks are thrown (the server has them)", on[4]).isTrue();
        config().oldItemDrop = false;
        boolean[] off = throwOutOfInventory(t);
        t.check("off: arm swings throwing one stick outside the window", off[0]).isTrue();
        t.check("off: arm swings throwing the rest outside the window", off[1]).isTrue();
        t.check("off: arm swings throwing a stack with Ctrl+Q over a slot", off[2]).isTrue();
        t.check("off: arm swings throwing out of the creative inventory", off[3]).isTrue();
        t.check("off: the sticks are thrown (the server has them)", off[4]).isTrue();
    }

    /** Drops the only item in the hand; returns {drawn item still the stick right after, lowest hand height} */
    private static boolean[] dropOnlyItem(TestContext t) {
        t.holdItem(new ItemStack(Items.STICK, 1));
        t.ticks(15);
        t.pressKey(t.mc().options.keyDrop.getDefaultKey().getValue());
        boolean stillDrawn = hand(t).devtest$getMainHandItem().is(Items.STICK);
        boolean lowered = lowestHand(t, 10) < 0.1;
        return new boolean[] {stillDrawn, lowered};
    }

    private void dropLastItem(TestContext t) {
        boolean[] on = dropOnlyItem(t);
        t.check("on: the dropped item is still drawn right after", on[0]).isTrue();
        t.check("on: the hand goes down before coming up empty", on[1]).isTrue();
        t.removeEntities();
        config().oldItemDrop = false;
        boolean[] off = dropOnlyItem(t);
        t.check("off: the dropped item is still drawn right after", off[0]).isFalse();
    }

    private void reequipAttack(TestContext t) {
        CombatTweaksFeature.getConfig().noAttackCooldown = false;
        t.holdItem(new ItemStack(Items.IRON_SWORD));
        t.ticks(30);
        t.player().resetAttackStrengthTicker();
        t.check("on: lowest hand height after an attack", lowestHand(t, 10)).near(1.0, 1.0E-5);
        t.ticks(30);
        config().legacyReequip = false;
        t.player().resetAttackStrengthTicker();
        t.check("off: lowest hand height after an attack", lowestHand(t, 10)).atMost(0.5);
    }

    /** Puts an item into the player's hand and returns the lowest hand height of the next 10 ticks */
    private static double swapAndWatch(TestContext t, ItemStack stack) {
        t.withServerPlayer(player -> player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy()));
        return lowestHand(t, 10);
    }

    private static ItemStack damagedPickaxe(int damage) {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.setDamageValue(damage);
        return pickaxe;
    }

    private void reequipDurability(TestContext t) {
        t.holdItem(damagedPickaxe(10));
        t.ticks(15);
        t.check("on: durability change, lowest hand height", swapAndWatch(t, damagedPickaxe(11)))
            .near(1.0, 1.0E-5);
        ItemStack named = damagedPickaxe(11);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            net.minecraft.network.chat.Component.literal("Renamed"));
        t.check("on: renamed item, lowest hand height (re-equip)", swapAndWatch(t, named)).atMost(0.1);
        t.ticks(10);
        config().legacyReequip = false;
        t.check("off: durability change, lowest hand height (re-equip)", swapAndWatch(t, damagedPickaxe(12)))
            .atMost(0.1);
    }

    /** Mines the stone block in front of the player in survival; returns the lowest hand height until 10 ticks after */
    private static double mineStone(TestContext t) {
        t.gameMode(GameType.SURVIVAL);
        t.site().set(4, 0, 6, "stone");
        // eyes at 1.62 over (4.5, 4.5) looking at the block's center (4.5, 0.5, 6.5): 29 degrees down
        t.teleport(4.5, 0.0, 4.5, 0.0F, 29.0F);
        t.setFlying(false);
        t.holdItem(new ItemStack(Items.IRON_PICKAXE));
        t.ticks(15);
        t.require(t.mc().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
            && hit.getBlockPos().equals(TestSite.ORIGIN.offset(4, 0, 6)), "The crosshair is not on the block: "
            + t.mc().hitResult);
        double lowest = hand(t).devtest$getMainHandHeight();
        // the game mines a held-down block only while it grabs the mouse (window focused), so the test calls the
        // same mining step the game calls every tick (Minecraft.continueAttack)
        BlockPos pos = TestSite.ORIGIN.offset(4, 0, 6);
        boolean broken = false;
        for (int i = 0; i < 100 && !broken; i++) {
            t.onClientThread(() -> {
                if (!t.player().level().getBlockState(pos).isAir()
                    && t.mc().gameMode.continueDestroyBlock(pos, Direction.NORTH)) {
                    t.player().swing(InteractionHand.MAIN_HAND);
                }
                return null;
            });
            t.tick();
            lowest = Math.min(lowest, hand(t).devtest$getMainHandHeight());
            broken = t.onServer(level -> level.getBlockState(pos).isAir());
        }
        t.require(broken, "The block was not mined");
        lowest = Math.min(lowest, lowestHand(t, 10));
        t.check("the pickaxe lost durability", t.player().getMainHandItem().getDamageValue()).isEqualTo(1);
        return lowest;
    }

    private void reequipMining(TestContext t) {
        t.check("on: lowest hand height while mining and after", mineStone(t)).near(1.0, 1.0E-5);
        config().legacyReequip = false;
        t.check("off: lowest hand height while mining and after (re-equip)", mineStone(t)).atMost(0.5);
    }

    /** Swings the held sword once with the attack key, at whatever the crosshair points to */
    private static double swingSword(TestContext t) {
        double lowest = hand(t).devtest$getMainHandHeight();
        t.click(t.mc().options.keyAttack);
        return Math.min(lowest, lowestHand(t, 12));
    }

    private void reequipSword(TestContext t) {
        t.gameMode(GameType.SURVIVAL);
        t.teleport(4.5, 0.0, 4.5, 0.0F, -40.0F);
        t.setFlying(false);
        t.holdItem(new ItemStack(Items.IRON_SWORD));
        t.ticks(30);
        // with No Attack Cooldown (Combat Tweaks, on by default) the charge never drops: test without it
        CombatTweaksFeature.getConfig().noAttackCooldown = false;
        t.check("on: swing in the air, lowest hand height", swingSword(t)).near(1.0, 1.0E-5);
        t.ticks(30);
        Spawned<Cow> cow = t.spawn(EntityType.COW, 4.5, 0.0, 6.0, 180.0F, entity -> {
            entity.setNoAi(true);
            AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(100.0);
            }
            entity.setHealth(100.0F);
        });
        t.teleport(4.5, 0.0, 4.5, 0.0F, 20.0F);
        t.ticks(30);
        t.require(t.mc().crosshairPickEntity != null && t.mc().crosshairPickEntity.getId() == cow.id(),
            "The crosshair is not on the cow");
        t.check("on: hitting a mob (sword loses durability), lowest hand height", swingSword(t)).near(1.0, 1.0E-5);
        t.check("the sword lost durability", t.player().getMainHandItem().getDamageValue()).isEqualTo(1);
        config().legacyReequip = false;
        t.ticks(30);
        t.check("off: hitting a mob, lowest hand height (dip)", swingSword(t)).atMost(0.5);
        t.teleport(4.5, 0.0, 4.5, 0.0F, -40.0F);
        t.ticks(30);
        t.check("off: swing in the air, lowest hand height (dip)", swingSword(t)).atMost(0.5);
    }

    /** Uses the held item once: holds the use key (a mouse button) for one tick */
    private static void useKey(TestContext t) {
        t.hold(t.mc().options.keyUse);
        t.tick();
        t.release(t.mc().options.keyUse);
    }

    private void useSwing(TestContext t) {
        t.gameMode(GameType.SURVIVAL);
        t.teleport(4.5, 0.0, 4.5, 0.0F, -30.0F);
        t.holdItem(new ItemStack(Items.SNOWBALL, 16));
        t.ticks(10);
        t.check("on: arm swings throwing a snowball", swingsAfter(t, () -> useKey(t))).isFalse();
        config().oldItemUse = false;
        t.ticks(10);
        t.check("off: arm swings throwing a snowball", swingsAfter(t, () -> useKey(t))).isTrue();
    }

    private void useDip(TestContext t) {
        // creative: throwing a snowball does not use it up, so the stack does not change
        t.teleport(4.5, 0.0, 4.5, 0.0F, -30.0F);
        t.holdItem(new ItemStack(Items.SNOWBALL, 16));
        t.ticks(15);
        useKey(t);
        t.check("creative (stack unchanged): lowest hand height", lowestHand(t, 8)).near(1.0, 1.0E-5);
        t.gameMode(GameType.SURVIVAL);
        t.ticks(15);
        useKey(t);
        t.check("survival (one snowball used): lowest hand height", lowestHand(t, 8)).atMost(0.5);
        config().oldItemUse = false;
        t.gameMode(GameType.CREATIVE);
        t.ticks(15);
        useKey(t);
        t.check("off, creative: lowest hand height", lowestHand(t, 8)).atMost(0.5);
    }

    private void serverSwingEcho(TestContext t) {
        t.check("on: swing sent by the server", swingsAfter(t, () ->
            t.withServerPlayer(player -> player.swing(InteractionHand.MAIN_HAND, true)))).isFalse();
        config().oldItemUse = false;
        // the server sends a new swing only once its last one is half done
        t.ticks(10);
        t.check("off: swing sent by the server", swingsAfter(t, () ->
            t.withServerPlayer(player -> player.swing(InteractionHand.MAIN_HAND, true)))).isTrue();
    }

    /** Spawns an armor stand (a living entity that is not a player) and makes the server swing its arm */
    private static boolean standSwings(TestContext t, ItemStack held) {
        Spawned<ArmorStand> stand = t.spawn(EntityType.ARMOR_STAND, 4.5, 0.0, 6.5, 180.0F,
            entity -> entity.setItemSlot(EquipmentSlot.MAINHAND, held));
        t.ticks(5);
        stand.server(entity -> {
            entity.swing(InteractionHand.MAIN_HAND);
            return null;
        });
        boolean swung = false;
        for (int i = 0; i < 5; i++) {
            t.tick();
            ArmorStand client = stand.client();
            swung |= client != null && client.swinging;
        }
        t.removeEntities();
        return swung;
    }

    private void mobSwing(TestContext t) {
        t.check("on: empty-handed mob swings", standSwings(t, ItemStack.EMPTY)).isFalse();
        t.check("on: mob with a sword swings", standSwings(t, new ItemStack(Items.IRON_SWORD))).isTrue();
        config().legacyMobSwing = false;
        t.check("off: empty-handed mob swings", standSwings(t, ItemStack.EMPTY)).isTrue();
    }

    // ---- other entities -------------------------------------------------------------------------------------------

    /** Spawns a pig that does not move by itself, in front of the player */
    private static Spawned<Pig> stillPig(TestContext t) {
        t.teleport(4.5, 1.0, 2.5, 0.0F, 20.0F);
        Spawned<Pig> pig = t.spawn(EntityType.PIG, 4.5, 0.0, 8.5, 0.0F, entity -> entity.setNoAi(true));
        t.ticks(20);
        return pig;
    }

    private static void movePig(TestContext t, Spawned<Pig> pig, double dx, float dYaw) {
        pig.server(entity -> {
            entity.setPos(entity.getX() + dx, entity.getY(), entity.getZ());
            entity.setYRot(entity.getYRot() + dYaw);
            entity.setYBodyRot(entity.getYRot());
            return null;
        });
        t.ticks(15);
    }

    private static double clientX(Spawned<Pig> pig) {
        Pig client = pig.client();
        return client == null ? Double.NaN : client.getX();
    }

    private void syncMove(TestContext t) {
        Spawned<Pig> pig = stillPig(t);
        double start = clientX(pig);
        movePig(t, pig, 0.05, 0.0F);
        t.check("on: a 0.05 move is not shown", clientX(pig) - start).near(0.0, 1.0E-6);
        movePig(t, pig, 0.2, 0.0F);
        double moved = clientX(pig);
        Double server = pig.server(entity -> entity.getX());
        t.check("on: after 0.25 the client moved", Math.abs(moved - start)).atLeast(0.2);
        t.check("on: drawn at whole 1/32 steps", Math.abs(moved * 32.0 - Math.round(moved * 32.0))).atMost(1.0E-6);
        t.check("on: within 1/32 of the server", Math.abs(moved - (server == null ? 0 : server))).atMost(1.0 / 32);
        config().legacyEntitySync = false;
        movePig(t, pig, 0.05, 0.0F);
        Double serverOff = pig.server(entity -> entity.getX());
        t.check("off: the client follows the server exactly", Math.abs(clientX(pig)
            - (serverOff == null ? 0 : serverOff))).atMost(1.0 / 4096);
    }

    private static float clientYaw(Spawned<Pig> pig) {
        Pig client = pig.client();
        return client == null ? Float.NaN : client.getYRot();
    }

    private void syncRotation(TestContext t) {
        Spawned<Pig> pig = stillPig(t);
        float start = clientYaw(pig);
        movePig(t, pig, 0.0, 3.0F);
        t.check("on: a 3 degree turn is not shown", (double) Mth.wrapDegrees(clientYaw(pig) - start))
            .near(0.0, 1.0E-4);
        movePig(t, pig, 0.0, 10.0F);
        float yaw = clientYaw(pig);
        double steps = yaw * 256.0 / 360.0;
        t.check("on: after 13 degrees the client turned", (double) Math.abs(Mth.wrapDegrees(yaw - start)))
            .atLeast(10.0);
        t.check("on: angle in whole 1/256 turns", Math.abs(steps - Math.round(steps))).atMost(1.0E-3);
        config().legacyEntitySync = false;
        float before = clientYaw(pig);
        movePig(t, pig, 0.0, 3.0F);
        t.check("off: a 3 degree turn is shown", (double) Math.abs(Mth.wrapDegrees(clientYaw(pig) - before)))
            .atLeast(2.0);
    }

    /** Turns the pig's head 40 degrees on the server; returns how far the client's head turned in its first tick */
    private static double headFirstTick(TestContext t, Spawned<Pig> pig) {
        Pig client = pig.client();
        t.require(client != null, "The pig is gone");
        float start = client.getYHeadRot();
        pig.server(entity -> {
            entity.setYHeadRot(entity.getYHeadRot() + 40.0F);
            return null;
        });
        for (int i = 0; i < 20; i++) {
            t.tick();
            float turned = Math.abs(Mth.wrapDegrees(client.getYHeadRot() - start));
            if (turned > 0.5F) {
                return turned;
            }
        }
        return 0.0;
    }

    private void syncHead(TestContext t) {
        Spawned<Pig> pig = stillPig(t);
        t.check("on: head turn in the first tick (of 40 degrees)", headFirstTick(t, pig)).near(40.0, 1.5);
        t.ticks(10);
        config().legacyEntitySync = false;
        t.check("off: head turn in the first tick (of 40 degrees)", headFirstTick(t, pig)).atMost(30.0);
    }

    /** Gives the pig a velocity on the server; returns the client's speeds in the ticks after it arrived */
    private static List<Double> pushedPig(TestContext t, Spawned<Pig> pig) {
        Pig client = pig.client();
        t.require(client != null, "The pig is gone");
        pig.server(entity -> {
            entity.setDeltaMovement(0.0, 0.0, 0.3);
            entity.hurtMarked = true;
            return null;
        });
        t.waitUntil("the client gets the velocity", () -> client.getDeltaMovement().horizontalDistance() > 0.1, 20);
        List<Double> speeds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            speeds.add(client.getDeltaMovement().horizontalDistance());
            t.tick();
        }
        t.note("client speeds after the push", speeds.stream().map(value -> format(value)).toList());
        return speeds;
    }

    private void mobPhysics(TestContext t) {
        // velocity sync off: only the velocity handling is compared
        config().legacyEntitySync = false;
        Spawned<Pig> pig = stillPig(t);
        List<Double> on = pushedPig(t, pig);
        t.check("on: the client slows the pig down by itself (speed after 2 ticks / first)", on.get(2) / on.get(0))
            .atMost(0.9);
        t.removeEntities();
        config().legacyMobPhysics = false;
        Spawned<Pig> other = stillPig(t);
        List<Double> off = pushedPig(t, other);
        // 1.21.1 only slows a remote mob by the air drag (0.98 per tick)
        t.check("off: the client keeps the velocity it got, less 0.98 per tick (after 2 ticks / first)",
            off.get(2) / off.get(0)).near(0.98 * 0.98, 0.005);
    }

    /** 1.7.10 {@code EntityLivingBase.func_110146_f} for a player standing still: the new body yaw */
    private static float legacyBody(float body, float yaw) {
        float headToBody = Mth.wrapDegrees(yaw - body);
        headToBody = Mth.clamp(headToBody, -75.0F, 75.0F);
        // 1.7.10 clamps with "< -75" and ">= 75"; both give 75 at 75
        float result = yaw - headToBody;
        if (headToBody * headToBody > 2500.0F) {
            result += headToBody * 0.2F;
        }
        return result;
    }

    /** Hovers still facing 0, then turns the head to 100 degrees; returns the body yaw of 3 ticks */
    private static List<Float> turnHead(TestContext t) {
        t.teleport(4.5, 2.0, 4.5, 0.0F, 0.0F);
        t.setFlying(true);
        t.ticks(10);
        // standing still, the body does not turn back by itself: face it to 0
        t.player().yBodyRot = 0.0F;
        t.player().yBodyRotO = 0.0F;
        t.ticks(5);
        t.require(Math.abs(Mth.wrapDegrees(t.player().yBodyRot)) < 0.5F, "The body did not face 0");
        List<Float> bodies = new ArrayList<>();
        t.face(100.0F);
        for (int i = 0; i < 3; i++) {
            t.tick();
            bodies.add(t.player().yBodyRot);
        }
        t.note("body yaw", bodies.stream().map(value -> format(value)).toList());
        return bodies;
    }

    private void bodyRotation(TestContext t) {
        List<Float> on = turnHead(t);
        float expected = 0.0F;
        for (int i = 0; i < 3; i++) {
            expected = legacyBody(expected, 100.0F);
            t.check("on: body yaw after " + (i + 1) + " ticks (1.7.10: 40, 52, 52)", (double) on.get(i))
                .near(expected, 1.0E-3);
        }
        config().legacyBodyRotation = false;
        List<Float> off = turnHead(t);
        t.check("off: body yaw after 1 tick (1.21.1: at most 50 degrees from the head)", (double) off.get(0))
            .near(50.0, 1.0E-3);
    }

    /** Every mob of the game except the ender dragon, which turns its own body in both versions */
    private static List<EntityType<?>> mobTypes(TestContext t) {
        return t.onServer(level -> {
            List<EntityType<?>> types = new ArrayList<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                Entity entity = type.create(level);
                if (entity instanceof Mob && !(entity instanceof EnderDragon)) {
                    types.add(type);
                }
            }
            return types;
        });
    }

    /** Spawns a mob that stays (no AI, kept) at a spot of the grid, facing 0 */
    private static <T extends Entity> Spawned<T> stillMob(TestContext t, EntityType<T> type, int index) {
        return t.spawn(type, 4.5 + index % 10 * 6, 0.0, 8.5 + index / 10 * 6, 0.0F, entity -> {
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setPersistenceRequired();
            }
        });
    }

    /**
     * Spawns every mob facing 0, then turns their heads to 170 degrees on the server (they look behind the way they
     * walked); returns each mob's angle between head and body on the client a while later
     */
    private static List<Double> headToBody(TestContext t, List<EntityType<?>> types) {
        t.command("difficulty easy");
        t.teleport(30.5, 12.0, -10.0, 0.0F, 30.0F);
        t.setFlying(true);
        List<Spawned<?>> mobs = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            mobs.add(stillMob(t, types.get(i), i));
        }
        t.ticks(10);
        t.onServer(level -> {
            for (Spawned<?> mob : mobs) {
                Entity entity = level.getEntity(mob.id());
                t.require(entity != null, "A mob is gone");
                entity.setYHeadRot(170.0F);
            }
            return null;
        });
        t.ticks(40);
        List<Double> angles = new ArrayList<>();
        for (Spawned<?> mob : mobs) {
            angles.add(mob.client() instanceof LivingEntity living
                ? (double) Math.abs(Mth.wrapDegrees(living.getYHeadRot() - living.yBodyRot)) : Double.NaN);
        }
        t.removeEntities();
        t.command("difficulty peaceful");
        return angles;
    }

    private void mobHeadLimit(TestContext t) {
        List<EntityType<?>> types = mobTypes(t);
        t.note("mobs", types.size());
        config().legacyBodyRotation = false;
        List<Double> off = headToBody(t, types);
        config().legacyBodyRotation = true;
        List<Double> on = headToBody(t, types);
        for (int i = 0; i < types.size(); i++) {
            String name = BuiltInRegistries.ENTITY_TYPE.getKey(types.get(i)).getPath();
            t.require(!off.get(i).isNaN() && !on.get(i).isNaN(), "The " + name + " is gone");
            // 1.7.10 kept the head within 75 degrees of the body; mobs whose body does not follow the head in 1.21.1
            // (shulker) keep that
            t.check("on: " + name + ": head to body (at most 75 or 1.21.1)", on.get(i))
                .atMost(Math.max(75.0, off.get(i)) + 0.01);
        }
    }

    /**
     * Walks mobs 8 blocks along +z, then 8 along +x, by their own navigation (their goals and brain cleared); returns
     * for each the median and largest angle between its body and the way it walks on the client, the moving ticks and
     * 1 if it turned the corner
     *
     * <p>The median leaves out single ticks where a mob stepping back a little at the corner seems to walk backwards
     * (in both versions); a body leaning while it walks shows in most ticks.</p>
     */
    private static List<double[]> walkAndTurn(TestContext t, List<EntityType<?>> types, boolean lookAtPlayer) {
        t.command("difficulty easy");
        t.teleport(36.5, 14.0, 28.5, 0.0F, 90.0F);
        t.setFlying(true);
        List<Spawned<?>> mobs = new ArrayList<>();
        List<Vec3> starts = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            Vec3 start = new Vec3(4.5 + i % 5 * 16, 0.0, 4.5 + i / 5 * 16);
            starts.add(start);
            mobs.add(t.spawn(types.get(i), start.x, start.y, start.z, 0.0F, entity -> {
                if (entity instanceof Mob mob) {
                    mob.setPersistenceRequired();
                    ((MobAccessor) mob).devtest$getGoalSelector().removeAllGoals(goal -> true);
                    ((MobAccessor) mob).devtest$getTargetSelector().removeAllGoals(goal -> true);
                    mob.getBrain().removeAllBehaviors();
                    mob.getBrain().clearMemories();
                }
            }));
        }
        t.ticks(10);
        Vec3 origin = Vec3.atLowerCornerOf(TestSite.ORIGIN);
        Vec3 eyes = t.player().getEyePosition();
        boolean[] turned = new boolean[mobs.size()];
        List<List<Double>> tilts = new ArrayList<>();
        for (int i = 0; i < mobs.size(); i++) {
            tilts.add(new ArrayList<>());
        }
        for (int tick = 0; tick < 110; tick++) {
            boolean first = tick == 0;
            t.onServer(level -> {
                for (int i = 0; i < mobs.size(); i++) {
                    if (!(level.getEntity(mobs.get(i).id()) instanceof Mob mob)) {
                        continue;
                    }
                    Vec3 corner = origin.add(starts.get(i)).add(0.0, 0.0, 8.0);
                    if (first) {
                        mob.getNavigation().moveTo(corner.x, corner.y, corner.z, 1.0);
                    } else if (!turned[i] && mob.getNavigation().isDone()) {
                        // reached the corner (wide mobs stop up to half their width before it)
                        mob.getNavigation().moveTo(corner.x + 8.0, corner.y, corner.z, 1.0);
                        turned[i] = true;
                    }
                    if (lookAtPlayer) {
                        mob.getLookControl().setLookAt(eyes.x, eyes.y, eyes.z);
                    }
                }
                return null;
            });
            t.tick();
            for (int i = 0; i < mobs.size(); i++) {
                if (mobs.get(i).client() instanceof Mob client) {
                    double dx = client.getX() - client.xo;
                    double dz = client.getZ() - client.zo;
                    if (dx * dx + dz * dz > 0.0025) {
                        double walkYaw = Mth.atan2(dz, dx) * Mth.RAD_TO_DEG - 90.0;
                        tilts.get(i).add(Math.abs(Mth.wrapDegrees(client.yBodyRot - walkYaw)));
                    }
                }
            }
        }
        t.removeEntities();
        t.command("difficulty peaceful");
        List<double[]> result = new ArrayList<>();
        for (int i = 0; i < mobs.size(); i++) {
            List<Double> sorted = tilts.get(i).stream().sorted().toList();
            double median = sorted.isEmpty() ? Double.NaN : sorted.get(sorted.size() / 2);
            double largest = sorted.isEmpty() ? Double.NaN : sorted.get(sorted.size() - 1);
            result.add(new double[] {median, largest, sorted.size(), turned[i] ? 1.0 : 0.0});
        }
        return result;
    }

    private void walkingBody(TestContext t) {
        // mobs that used the old AI in 1.7.10 and walk, and mobs with the 1.21.1 body rules to compare (slimes jump:
        // 1.7.10 turns them towards each jump, so they are left out)
        List<EntityType<?>> types = List.of(EntityType.ZOMBIFIED_PIGLIN, EntityType.ENDERMAN, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.GIANT, EntityType.SILVERFISH, EntityType.ZOMBIE, EntityType.CREEPER,
            EntityType.SKELETON);
        for (boolean look : new boolean[] {false, true}) {
            String scene = look ? "looking at the player" : "head free";
            List<double[]> on = walkAndTurn(t, types, look);
            config().legacyBodyRotation = false;
            List<double[]> off = walkAndTurn(t, types, look);
            config().legacyBodyRotation = true;
            for (int i = 0; i < types.size(); i++) {
                String name = BuiltInRegistries.ENTITY_TYPE.getKey(types.get(i)).getPath();
                t.require(on.get(i)[2] >= 10 && off.get(i)[2] >= 10, "The " + name + " did not walk");
                t.require(on.get(i)[3] > 0.0 && off.get(i)[3] > 0.0, "The " + name + " did not turn the corner");
                t.note(scene + ": " + name + ": largest angle, on / off", format(on.get(i)[1]) + " / "
                    + format(off.get(i)[1]));
                // a body leaning towards the head leans 20 to 30 degrees; 5 leaves room for the timing and for the
                // 1.7.10 turn of old-AI bodies (a few ticks behind at corners)
                t.check(scene + ": " + name + ": median angle between body and walk (at most 1.21.1 + 5)", on.get(i)[0])
                    .atMost(off.get(i)[0] + 5.0);
            }
        }
    }

    // ---- models ---------------------------------------------------------------------------------------------------

    private static PlayerModel<AbstractClientPlayer> playerModel(TestContext t) {
        EntityRenderer<?> renderer = t.mc().getEntityRenderDispatcher().getRenderer(t.player());
        return ((PlayerRenderer) renderer).getModel();
    }

    /**
     * Shows the player from behind (third person) while sneaking; returns {crouching, head y, right leg y} of the
     * model and how far down the player is drawn
     */
    private static double[] playerPose(TestContext t, boolean flying) {
        t.teleport(4.5, flying ? 3.0 : 0.0, 4.5, 0.0F, 10.0F);
        t.setFlying(flying);
        t.mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        t.ticks(10);
        t.hold(t.mc().options.keyShift);
        t.ticks(5);
        if (flying) {
            t.setFlying(true);
            t.ticks(1);
        }
        PlayerModel<AbstractClientPlayer> model = playerModel(t);
        double drawnLower = -t.mc().getEntityRenderDispatcher().getRenderer(t.player())
            .getRenderOffset(t.player(), 1.0F).y;
        double[] pose = {model.crouching ? 1 : 0, model.head.y, model.rightLeg.y, drawnLower};
        t.release(t.mc().options.keyShift);
        t.ticks(5);
        return pose;
    }

    private void sneakPose(TestContext t) {
        double[] on = playerPose(t, false);
        t.check("on: sneaking pose", on[0]).isEqualTo(1.0);
        t.check("on: head y (1.7.10: 1)", on[1]).near(1.0, 1.0E-5);
        t.check("on: leg y (1.7.10: 9)", on[2]).near(9.0, 1.0E-5);
        t.check("on: drawn lower by the eyes' offset (0.08)", on[3]).near(0.08, 1.0E-5);
        t.check("on, flying: sneaking pose while shift is held", playerPose(t, true)[0]).isEqualTo(1.0);
        config().legacySneakPose = false;
        double[] off = playerPose(t, false);
        t.check("off: head y (1.21.1: 4.2)", off[1]).near(4.2, 1.0E-5);
        t.check("off: leg y (1.21.1: 12.2)", off[2]).near(12.2, 1.0E-5);
        t.check("off: drawn lower (1.21.1: 0.125)", off[3]).near(0.125, 1.0E-5);
        t.check("off, flying: sneaking pose while shift is held", playerPose(t, true)[0]).isEqualTo(0.0);
    }

    /** Spawns a hostile mob that stays (easy difficulty, no AI) in view; returns its model's right arm x angle */
    private static <T extends Mob> double armAngle(TestContext t, EntityType<T> type) {
        t.command("difficulty easy");
        t.teleport(4.5, 1.0, 2.5, 0.0F, 15.0F);
        Spawned<T> mob = t.spawn(type, 4.5, 0.0, 6.5, 180.0F, entity -> {
            if (entity instanceof Mob created) {
                created.setNoAi(true);
                created.setPersistenceRequired();
            }
            if (entity instanceof Skeleton skeleton) {
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            }
        });
        t.ticks(10);
        Entity client = mob.client();
        t.require(client != null, "The mob is gone");
        EntityRenderer<?> renderer = t.mc().getEntityRenderDispatcher().getRenderer(client);
        double angle = renderer instanceof LivingEntityRenderer<?, ?> living
            && living.getModel() instanceof HumanoidModel<?> model ? model.rightArm.xRot : Double.NaN;
        t.require(!Double.isNaN(angle), "The mob has no humanoid model");
        t.removeEntities();
        t.command("difficulty peaceful");
        return angle;
    }

    private void zombieArms(TestContext t) {
        // arms bob by up to 0.05 radians around the pose
        t.check("on: zombie arm (1.7.10: -pi/2)", armAngle(t, EntityType.ZOMBIE)).near(-Math.PI / 2, 0.06);
        t.check("on: skeleton arm (1.7.10: -pi/2)", armAngle(t, EntityType.SKELETON)).near(-Math.PI / 2, 0.06);
        config().legacyZombieArms = false;
        t.check("off: zombie arm (1.21.1: -pi/2.25)", armAngle(t, EntityType.ZOMBIE)).near(-Math.PI / 2.25, 0.06);
    }

    /** Hurts a cow in front of the player at night and captures the frame while it is red */
    private static NativeImage hurtCowFrame(TestContext t) {
        t.command("time set midnight");
        t.teleport(4.5, 1.0, 2.0, 0.0F, 15.0F);
        Spawned<Cow> cow = t.spawn(EntityType.COW, 4.5, 0.0, 5.5, 180.0F, entity -> {
            entity.setNoAi(true);
            AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(100.0);
            }
            entity.setHealth(100.0F);
        });
        t.ticks(10);
        cow.server(entity -> {
            entity.hurt(entity.damageSources().generic(), 1.0F);
            return null;
        });
        Cow client = cow.client();
        t.require(client != null, "The cow is gone");
        t.waitUntil("the cow turns red", () -> client.hurtTime > 0, 20);
        NativeImage hurt = t.captureFrame();
        t.removeEntities();
        return hurt;
    }

    /** Average red in the middle of the frame, where the cow stands */
    private static double middleRed(NativeImage frame) {
        int width = frame.getWidth() / 4;
        int height = frame.getHeight() / 4;
        return Images.meanChannel(frame, (frame.getWidth() - width) / 2, (frame.getHeight() - height) / 2, width,
            height, 0);
    }

    private void hurtTint(TestContext t) {
        // at night the 1.21.1 red is darkened with the world; the 1.7.10 red uses the block brightness only
        t.mc().options.hideGui = true;
        try (NativeImage on = hurtCowFrame(t)) {
            t.saveFrame("hurt_tint_legacy_night", on);
            config().legacyHurtTint = false;
            try (NativeImage off = hurtCowFrame(t)) {
                t.saveFrame("hurt_tint_vanilla_night", off);
                t.note("average red in the middle: 1.7.10", middleRed(on));
                t.note("average red in the middle: 1.21.1", middleRed(off));
                t.check("at night the 1.7.10 red is brighter (difference of the average red)",
                    middleRed(on) - middleRed(off)).atLeast(3.0);
            }
        } finally {
            t.mc().options.hideGui = false;
        }
    }

    /** Share of green kept by the bright gray pixels of {@code before} (the skull and the sword) in {@code after} */
    private static double whiteKept(NativeImage before, NativeImage after) {
        long greenBefore = 0;
        long greenAfter = 0;
        long pixels = 0;
        for (int y = 0; y < before.getHeight(); y++) {
            for (int x = 0; x < before.getWidth(); x++) {
                int pixel = before.getPixelRGBA(x, y);
                int red = pixel & 0xFF;
                int green = pixel >> 8 & 0xFF;
                int blue = pixel >> 16 & 0xFF;
                if (red > 160 && green > 160 && blue > 160 && Math.abs(red - green) < 30 && Math.abs(red - blue) < 30) {
                    greenBefore += pixel >> 8 & 0xFF;
                    greenAfter += after.getPixelRGBA(x, y) >> 8 & 0xFF;
                    pixels++;
                }
            }
        }
        return pixels < 50 ? Double.NaN : (double) greenAfter / greenBefore;
    }

    private void hurtTintExcludes(TestContext t) {
        // 1.7.10 drew held items, blocks on heads, capes and arrows outside its red pass
        t.command("difficulty easy");
        t.mc().options.hideGui = true;
        t.teleport(4.5, 3.5, 4.5, 0.0F, 55.0F);
        Spawned<Zombie> zombie = t.spawn(EntityType.ZOMBIE, 4.5, 0.0, 6.5, 180.0F, entity -> {
            entity.setNoAi(true);
            entity.setPersistenceRequired();
            // a skull on its head (also keeps it from burning in the sun) and a sword in its hand: both are drawn
            // like entity models, so only the exclusion keeps them out of the red pass
            entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SKELETON_SKULL));
            entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(100.0);
            }
            entity.setHealth(100.0F);
        });
        t.ticks(20);
        try (NativeImage before = t.captureFrame()) {
            zombie.server(entity -> {
                entity.hurt(entity.damageSources().generic(), 1.0F);
                return null;
            });
            Zombie client = zombie.client();
            t.require(client != null, "The zombie is gone");
            t.waitUntil("the zombie turns red", () -> client.hurtTime > 0, 20);
            try (NativeImage hurt = t.captureFrame()) {
                t.saveFrame("hurt_tint_skull_and_sword", hurt);
                t.check("the skull and the sword keep their green while the zombie is red (share)",
                    whiteKept(before, hurt)).atLeast(0.95);
            }
        } finally {
            t.mc().options.hideGui = false;
            t.removeEntities();
            t.command("difficulty peaceful");
        }
    }

    // ---- 1.9 combat effects ---------------------------------------------------------------------------------------

    /** Hits a cow with a sword (sweep allowed); returns {sweep particles, damage indicator particles, attack sounds} */
    private static int[] swordHit(TestContext t) {
        CombatTweaksFeature.getConfig().disableSweepingAttack = false;
        t.teleport(3.0, 0.0, 4.5, -90.0F, 0.0F);
        t.setFlying(false);
        t.holdItem(new ItemStack(Items.IRON_SWORD));
        Spawned<Cow> cow = t.spawn(EntityType.COW, 4.5, 0.0, 4.5, 90.0F, entity -> {
            entity.setNoAi(true);
            AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(100.0);
            }
            entity.setHealth(100.0F);
        });
        t.ticks(10);
        t.clearParticles();
        SoundRecorder sounds = t.recordSounds();
        t.onServer(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            LivingEntity target = (LivingEntity) level.getEntity(cow.id());
            t.require(player != null && target != null, "The player or the cow is gone");
            player.attack(target);
            return null;
        });
        int sweep = 0;
        int indicators = 0;
        for (int i = 0; i < 5; i++) {
            t.tick();
            sweep = Math.max(sweep, t.particleCount(AttackSweepParticle.class));
            indicators = Math.max(indicators, t.particleCount(CritParticle.class));
        }
        int attackSounds = sounds.count("entity.player.attack");
        t.note("attack sounds", sounds.played("entity.player.attack").stream().map(played -> played.event())
            .toList());
        t.removeEntities();
        return new int[] {sweep, indicators, attackSounds};
    }

    private void combatEffects(TestContext t) {
        int[] on = swordHit(t);
        t.check("on: sweep particles", on[0]).isEqualTo(0);
        t.check("on: damage indicator particles", on[1]).isEqualTo(0);
        t.check("on: attack sounds (strong, sweep...)", on[2]).isEqualTo(0);
        config().noSweepEffect = false;
        config().noDamageIndicator = false;
        config().noAttackSounds = false;
        int[] off = swordHit(t);
        t.check("off: sweep particles", off[0]).atLeast(1);
        t.check("off: damage indicator particles", off[1]).atLeast(1);
        // 1.21.1 plays the sweep sound instead of the strong one for a sweep hit
        t.check("off: attack sounds (sweep)", off[2]).atLeast(1);
    }
}
