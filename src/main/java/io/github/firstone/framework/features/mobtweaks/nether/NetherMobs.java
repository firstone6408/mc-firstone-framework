package io.github.firstone.framework.features.mobtweaks.nether;

import com.mojang.serialization.Codec;
import io.github.firstone.framework.FirstOneFramework;
import io.github.firstone.framework.features.mobtweaks.MobTweaksConfig;
import io.github.firstone.framework.features.mobtweaks.MobTweaksFeature;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Nether rules for piglins and hoglins: they spawn only in the dark and start attacking a player only in the dark
 *
 * <p>Lit Nether paths and rails stay safe, while the dark Nether stays as dangerous as in 1.21.1. Only piglins and
 * hoglins are changed; zombified piglins, piglin brutes and zoglins are left as they are.</p>
 *
 * <ul>
 *   <li>Spawn Only in the Dark ({@link #blocksSpawn}): a piglin or hoglin spawns by itself (naturally) only where the
 *       block light is at most the limit. Spawners, spawn eggs, structures and breeding are not limited.</li>
 *   <li>Attack Only in the Dark ({@link #startOnlyInDark}, {@link #afterAiStep}): in the dark a piglin or hoglin
 *       attacks on sight as in 1.21.1. While the player stands where the block light is above the limit, it attacks
 *       only while it is angry at them ({@link #ANGER}): for the anger time (30 seconds by default, like a
 *       zombified piglin's 20 to 39) after the player hit it or one of its kind, after a piglin saw them open a
 *       chest or mine gold, or after it last chased them in the dark. So stepping into the light does not shake off
 *       a chase at once, and a lit path is safe again once the anger is over. Hitting one still calls its kind as in
 *       1.21.1.</li>
 * </ul>
 *
 * <p>Mobs a structure placed (the piglins and hoglins of a bastion) keep 1.21.1's behavior: they are marked when
 * they spawn ({@link #STRUCTURE_MOB}); one that spawned before the mod was added (or while the option was off) is
 * checked once, by whether it stands in a bastion. With both options off nothing is noted or saved on any mob. Only
 * block light counts (the Nether has no sky light); for the player, the brighter of their feet and eyes. Runs on the
 * server.</p>
 */
public final class NetherMobs {

    /** How far a hit hoglin's anger reaches the grown hoglins around it, in blocks (as far as a piglin calls) */
    public static final int GROUP_RANGE = 16;

    /** The brightest light level */
    public static final int MAX_LIGHT = 15;

    /**
     * Saved on a piglin or hoglin a structure (a bastion) placed while Attack Only in the Dark is on; nothing is saved
     * on any other mob, and nothing at all while the option is off
     */
    public static final AttachmentType<Boolean> STRUCTURE_MOB = AttachmentRegistry.createPersistent(
        ResourceLocation.fromNamespaceAndPath(FirstOneFramework.MOD_ID, "nether_structure_mob"), Codec.BOOL);

    /** Whether an unmarked mob stands in a bastion, checked once while it is loaded; not saved */
    public static final AttachmentType<Boolean> IN_BASTION = AttachmentRegistry.create(
        ResourceLocation.fromNamespaceAndPath(FirstOneFramework.MOD_ID, "nether_in_bastion"));

    /** Until which game time a piglin or hoglin is angry at each player, by UUID; not saved (the anger is short) */
    public static final AttachmentType<Map<UUID, Long>> ANGER = AttachmentRegistry.create(
        ResourceLocation.fromNamespaceAndPath(FirstOneFramework.MOD_ID, "nether_anger"));

    private NetherMobs() {}

    /**
     * Registers the attachments (by loading this class) before any world is read
     *
     * <p>Called once from {@code MobTweaksFeature.initialize()}.</p>
     */
    public static void register() {
        // the attachments are registered when the class loads
    }

    /**
     * Tells whether a natural spawn of a piglin or hoglin must not happen here, because it is too bright
     *
     * <p>Called after the game's own spawn rules allowed the spawn.</p>
     *
     * @param type      the mob type
     * @param level     the level
     * @param spawnType why the mob spawns
     * @param pos       where its feet will be
     * @return true if the spawn is refused
     */
    public static boolean blocksSpawn(EntityType<?> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos) {
        MobTweaksConfig config = MobTweaksFeature.getConfig();
        return config.netherSpawnInDark && (type == EntityType.PIGLIN || type == EntityType.HOGLIN)
            && (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION)
            && level.getBrightness(LightLayer.BLOCK, pos) > limit(config.netherSpawnMaxLight);
    }

    /**
     * Notes whether a piglin or hoglin was placed by a structure, when it spawns
     *
     * @param mob       the piglin or hoglin
     * @param spawnType why it spawns
     */
    public static void noteSpawn(Mob mob, MobSpawnType spawnType) {
        if (spawnType == MobSpawnType.STRUCTURE && MobTweaksFeature.getConfig().netherAttackInDark) {
            mob.setAttached(STRUCTURE_MOB, true);
        }
    }

    /**
     * Notes that a piglin or hoglin became angry at an entity (it or one of its kind was hit; for a piglin also a
     * chest or gold); for a player, it stays angry at them for the anger time
     *
     * @param mob    the piglin or hoglin
     * @param target whom it is angry at
     */
    public static void noteAnger(Mob mob, LivingEntity target) {
        if (target instanceof Player player && MobTweaksFeature.getConfig().netherAttackInDark) {
            angryUntil(mob, player.getUUID(), mob.level().getGameTime() + angerTicks());
        }
    }

    /** Makes a mob angry at a player until a game time (never shorter than it already is); forgets old anger */
    private static void angryUntil(Mob mob, UUID player, long until) {
        long now = mob.level().getGameTime();
        Map<UUID, Long> old = mob.getAttached(ANGER);
        if (old != null && old.getOrDefault(player, Long.MIN_VALUE) >= until) {
            return;
        }
        Map<UUID, Long> anger = new HashMap<>();
        if (old != null) {
            old.forEach((uuid, time) -> {
                if (time >= now) {
                    anger.put(uuid, time);
                }
            });
        }
        anger.put(player, until);
        mob.<Map<UUID, Long>>setAttached(ANGER, Map.copyOf(anger));
    }

    /**
     * Notes that a player hit a hoglin: it and the grown hoglins within {@link #GROUP_RANGE} blocks stay angry at the
     * player for the anger time, and each attacks once it sees the player
     *
     * <p>1.21.1 calls only the grown hoglins the hit one can see, which goes unnoticed because in the dark every
     * hoglin attacks on sight anyway; in the light one behind a tree would stay calm, so the group is taken as piglins
     * take it: every grown one nearby, seen or not. Only a hit by the player starts the anger again, as for a
     * zombified piglin; the hoglins' own hits (which also call the others in 1.21.1) do not, so they calm down in the
     * light once the time is over.</p>
     *
     * @param hoglin   the hoglin that was hit
     * @param attacker who hit it
     */
    public static void noteHoglinHurt(Hoglin hoglin, LivingEntity attacker) {
        if (!(attacker instanceof Player) || !MobTweaksFeature.getConfig().netherAttackInDark) {
            return;
        }
        noteAnger(hoglin, attacker);
        hoglin.level().getEntitiesOfClass(Hoglin.class, hoglin.getBoundingBox().inflate(GROUP_RANGE),
            other -> other != hoglin && other.isAdult()).forEach(other -> noteAnger(other, attacker));
    }

    /**
     * Shares a hoglin's anger with the grown hoglins it calls to fight (1.21.1 calls the ones it sees whenever it is
     * hit or hits its target): they stay angry until the same time, never longer, so the group calms down together
     *
     * <p>Without this, a called hoglin that is not angry would be given the target by 1.21.1 and lose it again at once
     * in the light, turning to fighting for a moment.</p>
     *
     * @param hoglin the hoglin calling the others
     * @param target whom they are called to fight
     */
    public static void shareHoglinAnger(Hoglin hoglin, LivingEntity target) {
        Map<UUID, Long> anger = hoglin.getAttached(ANGER);
        Long until = anger == null ? null : anger.get(target.getUUID());
        if (!(target instanceof Player) || until == null || until < hoglin.level().getGameTime()) {
            return;
        }
        hoglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS).orElse(List.of())
            .forEach(other -> angryUntil(other, target.getUUID(), until));
    }

    /**
     * Checks a piglin or hoglin after its AI ran: while it attacks a player in the dark it stays angry at them;
     * when the player stands in the light and the anger is over, it stops
     *
     * <p>Called every server tick for each piglin and hoglin; does nothing more than read its target unless that is
     * a player.</p>
     *
     * @param mob the piglin or hoglin
     */
    public static void afterAiStep(Mob mob) {
        if (!MobTweaksFeature.getConfig().netherAttackInDark
            || !(mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) instanceof Player player)
            || isStructureMob(mob)) {
            return;
        }
        if (!inLight(mob, player)) {
            // chased in the dark: the anger lasts from now (renewed once a second)
            Map<UUID, Long> anger = mob.getAttached(ANGER);
            long now = mob.level().getGameTime();
            if (anger == null || anger.getOrDefault(player.getUUID(), Long.MIN_VALUE) < now + angerTicks() - 20) {
                noteAnger(mob, player);
            }
        } else if (!isAngryAt(mob, player)) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    /**
     * Wraps the function a piglin or hoglin uses to choose whom to start attacking, so it does not start on a player
     * standing in the light (unless it is angry at them)
     *
     * <p>Used only where the mob starts attacking; the check that makes it stop is left as in 1.21.1.</p>
     *
     * @param find the game's function
     * @param <E>  the mob class
     * @return the wrapped function
     */
    public static <E extends Mob> Function<E, Optional<? extends LivingEntity>> startOnlyInDark(
        Function<E, Optional<? extends LivingEntity>> find) {
        return mob -> {
            Optional<? extends LivingEntity> target = find.apply(mob);
            if (target.isPresent() && target.get() instanceof Player player && calmTowards(mob, player)) {
                return Optional.empty();
            }
            return target;
        };
    }

    /** Tells whether a mob must not start attacking a player it sees, because the player stands in the light */
    private static boolean calmTowards(Mob mob, Player player) {
        return MobTweaksFeature.getConfig().netherAttackInDark && inLight(mob, player) && !isAngryAt(mob, player)
            && !isStructureMob(mob);
    }

    /**
     * Tells whether a player is in block light above the attack limit: the brighter of the block at their feet and
     * the block at their eyes counts, since the feet can be inside a block (sitting in a minecart on a rail, standing
     * on a slab or soul sand), where the light is 0
     */
    private static boolean inLight(Mob mob, Player player) {
        BlockPos eyes = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        int light = Math.max(mob.level().getBrightness(LightLayer.BLOCK, player.blockPosition()),
            mob.level().getBrightness(LightLayer.BLOCK, eyes));
        return light > limit(MobTweaksFeature.getConfig().netherAttackMaxLight);
    }

    /** Returns the anger time in ticks (at least one second, also for a value edited by hand) */
    private static long angerTicks() {
        return Math.max(1, MobTweaksFeature.getConfig().netherAngerSeconds) * 20L;
    }

    /** Returns a light limit from the config as a light level (a value edited by hand outside 0 to 15 is clamped) */
    private static int limit(int value) {
        return Math.clamp(value, 0, MAX_LIGHT);
    }

    /**
     * Tells whether a piglin or hoglin is angry at a player ({@link #ANGER}); a piglin also while the game rule
     * universalAnger makes it angry at every player (1.21.1)
     *
     * <p>Each player has their own anger: another player hitting the mob does not end it.</p>
     *
     * @param mob    the piglin or hoglin
     * @param player the player
     * @return true while the mob is angry at the player
     */
    public static boolean isAngryAt(Mob mob, Player player) {
        if (mob instanceof Piglin piglin && piglin.getBrain().hasMemoryValue(MemoryModuleType.UNIVERSAL_ANGER)) {
            return true;
        }
        Map<UUID, Long> anger = mob.getAttached(ANGER);
        return anger != null && anger.getOrDefault(player.getUUID(), Long.MIN_VALUE) >= mob.level().getGameTime();
    }

    /**
     * Tells whether a mob was placed by a structure: marked when it spawned, else (spawned before the mod, or while
     * the option was off) whether it stands in a bastion, checked once while it is loaded
     */
    private static boolean isStructureMob(Mob mob) {
        if (mob.hasAttached(STRUCTURE_MOB)) {
            return true;
        }
        Boolean inBastion = mob.getAttached(IN_BASTION);
        if (inBastion == null) {
            inBastion = mob.level() instanceof ServerLevel level && inBastion(level, mob.blockPosition());
            mob.setAttached(IN_BASTION, inBastion);
        }
        return inBastion;
    }

    /** Tells whether a position is inside a piece of a bastion */
    private static boolean inBastion(ServerLevel level, BlockPos pos) {
        Structure bastion = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
            .get(BuiltinStructures.BASTION_REMNANT);
        return bastion != null && level.structureManager().getStructureWithPieceAt(pos, bastion).isValid();
    }
}
