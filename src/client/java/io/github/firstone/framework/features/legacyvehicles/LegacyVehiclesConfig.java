package io.github.firstone.framework.features.legacyvehicles;

/**
 * Holds the settings of the Legacy Vehicles feature
 *
 * <p>Every value in this class is saved to {@code legacy_vehicles.json} and loaded when the game starts.</p>
 *
 * <p>Handling, riding and splash change every boat; the seat and the model only change wooden boats (the only boat
 * of 1.7.10), as chest boats and bamboo rafts have no 1.7.10 look. This feature works on the client only.</p>
 */
public class LegacyVehiclesConfig {

    /**
     * 1.7.10 boat handling for the boat you drive: it goes where you look when you press forward, speeds up slowly
     * to 0.35 blocks per tick, turns toward where it moves and floats like 1.7.10; boats nobody rides move on your
     * client like 1.7.10, and your boat takes the pushes the server sends (Legacy Mechanics)
     */
    public boolean legacyBoatHandling = true;

    /**
     * With the 1.7.10 handling, boats slide on ice, packed ice, blue ice (and other slippery blocks) as fast as in
     * 1.21.1, still steered like 1.7.10 (1.7.10 boats could not slide on ice)
     */
    public boolean iceBoating = true;

    /**
     * 1.7.10 riding: look all around while in a boat (the boat does not turn you), sitting a little lower and 0.4
     * blocks behind the middle when alone in a wooden boat
     */
    public boolean legacyBoatRiding = true;

    /**
     * Mobs in boats turn their head and body as on the ground: the head as the server sends it (without shaking with
     * "Legacy Entity Sync"), the body following it, instead of facing the boat's front with the head twisted up to 105
     * degrees. Only how this client shows them: where they look is still the server's.
     */
    public boolean mobsTurnInBoats = true;

    /**
     * 1.7.10 hands while paddling: the hand and the held item stay up, and attacking and using items still work
     * (1.21.1 lowers both hands and blocks clicks while the boat's movement keys are held)
     */
    public boolean legacyBoatHands = true;

    /** Draw wooden boats with the flat 1.7.10 model, covered with the planks of the boat's wood */
    public boolean legacyBoatModel = true;

    /**
     * Keep the 1.21.1 model for a boat whose texture a resource pack changes, so the pack's look is not lost
     * (the pack's texture only fits the 1.21.1 model)
     */
    public boolean resourcePackBoats = true;

    /** Hide the paddles of every boat drawn with the 1.21.1 model (1.7.10 boats had none) */
    public boolean hidePaddles = true;

    /** 1.7.10 water splash around fast boats */
    public boolean boatSplash = true;
}
