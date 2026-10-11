package io.github.firstone.framework.features.legacymechanics.food;

import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;

/**
 * 1.7.10 natural regeneration rules, used by the server's food tick
 *
 * <p>1.7.10 {@code FoodStats.onUpdate} had one rule: with food at 18 or more and health not full, heal 1 point every
 * 80 ticks and add 3 exhaustion. 1.21.1 {@code FoodData.tick} first tries a second, faster rule: with food at 20 and
 * saturation left, heal {@code min(saturation, 6) / 6} every 10 ticks, at the cost of that much exhaustion; its slow
 * rule costs 6 exhaustion per point. Without the fast rule, the slow rule is 1.7.10's again (same food level, same
 * timer); the exhaustion and starving parts of the tick are the same in both versions and stay untouched.</p>
 */
public final class LegacyRegeneration {

    /** Exhaustion added for each point healed by the slow rule (1.7.10 {@code addExhaustion(3.0F)}) */
    private static final float EXHAUSTION_PER_HEAL = 3.0F;

    private LegacyRegeneration() {}

    /**
     * Tells whether the fast saturation rule may heal (it is used only when the player is hurt)
     *
     * @param hurt whether the player is hurt, as 1.21.1 checks for the fast rule
     * @return false when "Legacy Regeneration" is on (no fast rule in 1.7.10), otherwise {@code hurt}
     */
    public static boolean allowsSaturationHealing(boolean hurt) {
        return hurt && !LegacyMechanicsFeature.getConfig().legacyRegeneration;
    }

    /**
     * Returns the exhaustion the slow rule adds for one point healed
     *
     * @param exhaustion what 1.21.1 adds (6)
     * @return 3 like 1.7.10 when "Legacy Regeneration" is on, otherwise {@code exhaustion}
     */
    public static float exhaustionPerHeal(float exhaustion) {
        return LegacyMechanicsFeature.getConfig().legacyRegeneration ? EXHAUSTION_PER_HEAL : exhaustion;
    }
}
