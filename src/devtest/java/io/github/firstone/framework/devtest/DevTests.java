package io.github.firstone.framework.devtest;

import io.github.firstone.framework.devtest.core.FeatureTests;
import io.github.firstone.framework.devtest.features.appearance.AppearanceTests;
import io.github.firstone.framework.devtest.features.combattweaks.CombatTweaksTests;
import io.github.firstone.framework.devtest.features.legacyhud.LegacyHudTests;
import io.github.firstone.framework.devtest.features.legacymechanics.LegacyMechanicsTests;
import io.github.firstone.framework.devtest.features.legacyvehicles.LegacyVehiclesTests;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The tests of every feature
 *
 * <p>To test a new feature: write a {@link FeatureTests} class in {@code features/<feature>/} and add one line
 * here.</p>
 */
public final class DevTests {

    /**
     * Every feature's tests, in the order they run with {@code all} (Appearance last: a failed quit confirmation test
     * would close the game)
     */
    private static final List<Supplier<FeatureTests>> ALL = List.of(
        LegacyMechanicsTests::new,
        CombatTweaksTests::new,
        LegacyHudTests::new,
        LegacyVehiclesTests::new,
        AppearanceTests::new
    );

    private DevTests() {}

    /**
     * Returns the tests of the asked features
     *
     * @param ids {@code all}, or feature ids separated by commas (e.g. {@code legacy_vehicles})
     * @return the features' tests, in the order of {@link #ALL}
     * @throws IllegalArgumentException if an id has no tests
     */
    public static List<FeatureTests> select(String ids) {
        List<FeatureTests> all = ALL.stream().map(tests -> tests.get()).toList();
        if (ids.trim().equalsIgnoreCase("all")) {
            return all;
        }
        List<FeatureTests> selected = new ArrayList<>();
        for (String id : ids.split(",")) {
            FeatureTests tests = all.stream().filter(t -> t.id().equals(id.trim())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No dev tests for feature '" + id.trim()
                    + "'; known: " + all.stream().map(known -> known.id()).toList()));
            selected.add(tests);
        }
        return selected;
    }
}
