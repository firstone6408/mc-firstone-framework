package io.github.firstone.framework.features.legacymechanics.boat;

import io.github.firstone.framework.common.boat.BoatPushPayload;
import io.github.firstone.framework.common.boat.LegacyBoatPhysics;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.7.10 boat rules ({@code EntityBoat.onUpdate}), run on the server
 *
 * <ul>
 *   <li><b>Breaking</b> — a boat that hits a wall while faster than 0.2 blocks per tick breaks into 3 planks of its
 *       wood and 2 sticks (when the {@code doEntityDrops} rule is on), and removes the snow layers it goes through.
 *       Wooden boats always; chest boats and bamboo rafts when "Break Chest Boats and Rafts" is on (a chest boat
 *       also drops its chest and everything in it). Boats on slippery blocks (ice) do not break when "Safe on Ice"
 *       is on.</li>
 *   <li><b>Coasting</b> — when the driver gets out, the boat keeps the driver's speed and slows down with the
 *       1.7.10 motion ({@link LegacyBoatPhysics}), like every boat without a driver. Someone standing on such a boat
 *       and the boat push each other apart, as in 1.7.10 ({@link LegacyBoatPhysics#pushStanders}), and boats with
 *       passengers are pushed by what they touch ({@link #pushesOccupied}).</li>
 * </ul>
 *
 * <p>1.21.1 already breaks boats that fall more than 3 blocks onto land, and lily pads that boats touch.</p>
 *
 * <p>A boat with a driver is moved by the driver's client, which sends its new position every tick: the server does
 * not know the boat's speed, only how far it moved. So a crash is found from the previous move: if the boat moved
 * faster than 0.2 and the same move from where it is now runs into blocks, it hit them. A boat without a driver is
 * moved by the server, which knows both. The boat breaks at its next tick, never in the middle of a move.</p>
 */
public final class LegacyBoatRules {

    /** Speed above which a boat breaks when it hits a wall */
    private static final double CRASH_SPEED = 0.2;

    /** A move older than this many ticks is not used (the boat stopped or changed driver) */
    private static final int MAX_MOVE_AGE = 2;

    private LegacyBoatRules() {}

    /**
     * Tells whether boats without a driver move with the 1.7.10 motion
     *
     * @return true if the "Legacy Boat Coasting" option is on
     */
    public static boolean coasting() {
        return LegacyMechanicsFeature.getConfig().legacyBoatCoasting;
    }

    /**
     * Called on the server after a driver's client moved its boat
     *
     * @param boat the boat
     * @param move how far it moved
     */
    public static void afterDriverMove(Boat boat, Vec3 move) {
        State state = state(boat);
        boolean recent = boat.tickCount - state.lastMoveTick <= MAX_MOVE_AGE;
        if (breaks(boat)) {
            if (recent && state.lastMove.horizontalDistance() > CRASH_SPEED && hitsBlocks(boat, state.lastMove)) {
                state.crashed = true;
            }
            removeSnow(boat);
        }
        state.lastMove = move;
        state.lastMoveTick = boat.tickCount;
    }

    /**
     * Called on the server after the server moved a boat without a driver
     *
     * <p>With "Legacy Boat Coasting" the crash speed is the speed at the start of the tick, as in 1.7.10 (before the
     * land friction halves it); otherwise it is the motion the boat moved with.</p>
     *
     * @param boat the boat
     * @param move the motion it moved with
     */
    public static void afterServerMove(Boat boat, Vec3 move) {
        if (breaks(boat)) {
            double speed = coasting() ? handling(boat).startSpeed() : move.horizontalDistance();
            if (boat.horizontalCollision && speed > CRASH_SPEED) {
                state(boat).crashed = true;
            }
            removeSnow(boat);
        }
    }

    /**
     * Called on the server at the start of each boat tick: breaks the boat if it crashed since the last tick, and
     * gives a boat whose driver just got out the driver's last speed (with "Legacy Boat Coasting")
     *
     * @param boat the boat
     */
    public static void tick(Boat boat) {
        State state = state(boat);
        if (state.crashed) {
            state.crashed = false;
            if (breaks(boat) && !boat.isRemoved()) {
                breakApart(boat);
                return;
            }
        }

        boolean driven = boat.getControllingPassenger() instanceof Player;
        if (state.driven && !driven && coasting() && boat.tickCount - state.lastMoveTick <= MAX_MOVE_AGE) {
            // the server sets a driven boat's motion to zero every tick; the driver's last move is its real motion
            boat.setDeltaMovement(state.lastMove);
        }
        state.driven = driven;
        if (!driven && coasting()) {
            // the server pushes back the mobs standing on the boat; players move themselves on their client
            LegacyBoatPhysics.pushStanders(boat, true, entity -> !(entity instanceof Player));
        }
    }

    /**
     * Returns the 1.7.10 motion state of a boat, used by the server for boats without a driver
     *
     * @param boat the boat
     * @return the boat's motion state, never null
     */
    public static LegacyBoatPhysics.Handling handling(Boat boat) {
        return state(boat).handling;
    }

    /**
     * Removes the boats from the collisions the server checks a player's move against
     * ({@code ServerGamePacketListenerImpl.isPlayerCollidingWithAnythingNew}), with "Legacy Boat Coasting"
     *
     * <p>Each side moves a boat without a driver itself, as in 1.7.10, so the server's boat bobs a little apart from
     * the one the player stands on in their game. 1.21.1 puts a player whose new position touches something new
     * (here: the server's boat) back where they were, which shakes a player standing on a boat; 1.7.10 had no such
     * check. Blocks, the world border and other entities are still checked.</p>
     *
     * @param level  the player's level
     * @param box    the box the collisions were looked up in
     * @param shapes the collisions found there
     * @return {@code shapes} without the boxes of boats
     */
    public static Iterable<VoxelShape> withoutBoats(LevelReader level, AABB box, Iterable<VoxelShape> shapes) {
        if (!coasting() || !(level instanceof Level world)) {
            return shapes;
        }
        List<AABB> boats = world.getEntitiesOfClass(Boat.class, box.inflate(1.0E-7)).stream()
            .map(Boat::getBoundingBox)
            .toList();
        if (boats.isEmpty()) {
            return shapes;
        }
        List<VoxelShape> kept = new ArrayList<>();
        for (VoxelShape shape : shapes) {
            if (shape.isEmpty() || !boats.contains(shape.bounds())) {
                kept.add(shape);
            }
        }
        return kept;
    }

    /**
     * Tells whether a boat with passengers may be pushed by the entities it touches on the server, like 1.7.10, which
     * pushed boats whoever sat in them ({@code Entity.applyEntityCollision}); 1.21.1 never pushes an entity that
     * carries passengers ({@code Entity.push}), with "Legacy Boat Coasting"
     *
     * <p>A boat without a player driver (e.g. with a mob inside) is pushed on the server; the push of a boat a player
     * drives goes to that player's client ({@link #sendPushToDriver}).</p>
     *
     * @param entity an entity about to be pushed on the server
     * @return true for a boat
     */
    public static boolean pushesOccupied(Entity entity) {
        return entity instanceof Boat && !entity.level().isClientSide() && coasting();
    }

    /**
     * Sends the push of a boat a player drives to that player's client instead of pushing the server's boat
     *
     * <p>The driver's client moves the boat; pushing the server's boat would only make the game send the client the
     * server's motion of that boat, which is zero, and stop it. A client without the mod is not sent the push (its
     * boat is not pushed, as in 1.21.1).</p>
     *
     * @param entity the entity being pushed on the server
     * @param x      push along x
     * @param z      push along z
     * @return true if the push was handled here (the server's boat must not be pushed)
     */
    public static boolean sendPushToDriver(Entity entity, double x, double z) {
        if (!(entity instanceof Boat boat) || boat.level().isClientSide() || !coasting()
            || !(boat.getControllingPassenger() instanceof ServerPlayer driver)) {
            return false;
        }
        if (ServerPlayNetworking.canSend(driver, BoatPushPayload.TYPE)) {
            ServerPlayNetworking.send(driver, new BoatPushPayload(boat.getId(), x, z));
        }
        return true;
    }

    /**
     * Tells whether the water splash of an entity entering water must be left out for a boat (on the server: its
     * sound), with "Legacy Boat Coasting"
     *
     * <p>A boat floating with the 1.7.10 motion has its bottom right at the water's surface and bobs across it, so
     * 1.21.1 would splash each time it dips back in. A boat that falls into water still splashes
     * ({@link LegacyBoatPhysics#isBobbing}).</p>
     *
     * @param entity an entity entering water on the server
     * @return true for a floating boat that only bobs
     */
    public static boolean skipsSplash(Entity entity) {
        return entity instanceof Boat boat && !boat.level().isClientSide() && coasting()
            && LegacyBoatPhysics.isBobbing(boat);
    }

    /** Tells whether the breaking rules apply to this boat now */
    private static boolean breaks(Boat boat) {
        LegacyMechanicsConfig config = LegacyMechanicsFeature.getConfig();
        return config.legacyBoatBreaking && (LegacyBoatPhysics.isWoodenBoat(boat) || config.breakChestBoatsAndRafts)
            && !(config.boatsSafeOnIce && LegacyBoatPhysics.slideFriction(boat) > 0.0F);
    }

    /** Tells whether moving the boat sideways by {@code move} from where it is runs into blocks */
    private static boolean hitsBlocks(Boat boat, Vec3 move) {
        // a little lower and higher than the boat, so a floor or ceiling it touches is not counted
        AABB box = boat.getBoundingBox().move(move.x, 0.0, move.z).inflate(0.0, -0.01, 0.0);
        for (VoxelShape shape : boat.level().getBlockCollisions(boat, box)) {
            if (!shape.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the boat and drops 3 planks of its wood and 2 sticks, as 1.21.1 does when a boat falls too far, plus
     * the chest of a chest boat (the chest's contents drop when the chest boat is removed, see
     * {@code ChestBoat.remove})
     */
    private static void breakApart(Boat boat) {
        boat.kill();
        if (boat.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            for (int i = 0; i < 3; i++) {
                boat.spawnAtLocation(boat.getVariant().getPlanks());
            }
            for (int i = 0; i < 2; i++) {
                boat.spawnAtLocation(Items.STICK);
            }
            if (boat instanceof ChestBoat) {
                boat.spawnAtLocation(Items.CHEST);
            }
        }
    }

    /**
     * Removes snow layers under the boat's four corners, at its height and one block above, like 1.7.10
     * (the 1.7.10 boat position was 0.3 blocks above the bottom of the boat)
     */
    private static void removeSnow(Boat boat) {
        Level level = boat.level();
        int bottom = Mth.floor(boat.getY() + 0.3);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int corner = 0; corner < 4; corner++) {
            int x = Mth.floor(boat.getX() + (corner % 2 - 0.5) * 0.8);
            int z = Mth.floor(boat.getZ() + (corner / 2 - 0.5) * 0.8);
            for (int y = bottom; y <= bottom + 1; y++) {
                if (level.getBlockState(pos.set(x, y, z)).is(Blocks.SNOW)) {
                    level.removeBlock(pos, false);
                }
            }
        }
    }

    /** Returns the server state kept in a boat */
    private static State state(Boat boat) {
        return ((Holder) boat).legacyMechanics$boatState();
    }

    /** Server state of one boat, kept in the boat by {@code LegacyBoatRulesMixin} */
    public static final class State {

        /** 1.7.10 motion of the boat while it has no driver */
        private final LegacyBoatPhysics.Handling handling = new LegacyBoatPhysics.Handling();

        /** Last move sent by the driver's client */
        private Vec3 lastMove = Vec3.ZERO;

        /** Boat tick of {@link #lastMove} */
        private int lastMoveTick;

        /** The boat had a player driver at its last tick */
        private boolean driven;

        /** The boat crashed and breaks at its next tick */
        private boolean crashed;
    }

    /** Implemented by {@link Boat} (through {@code LegacyBoatRulesMixin}) to keep its {@link State} */
    public interface Holder {

        /**
         * Returns the server state of this boat
         *
         * @return the state, never null
         */
        State legacyMechanics$boatState();
    }
}
