package io.github.firstone.framework.devtest;

import io.github.firstone.framework.devtest.core.FeatureTests;
import io.github.firstone.framework.devtest.features.animatium.AnimatiumTests;
import io.github.firstone.framework.devtest.features.appearance.AppearanceTests;
import io.github.firstone.framework.devtest.features.combattweaks.CombatTweaksTests;
import io.github.firstone.framework.devtest.features.cosmetics.CosmeticsTests;
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
        CosmeticsTests::new,
        AnimatiumTests::new,
        LegacyVehiclesTests::new,
        AppearanceTests::new
    );

    private DevTests() {}

    /**
     * Returns the tests of the asked features
     *
     * @param ids  {@code all}, or feature ids separated by commas (e.g. {@code legacy_vehicles})
     * @param skip feature ids to leave out, separated by commas (may be empty)
     * @return the features' tests, in the order of {@link #ALL}
     * @throws IllegalArgumentException if an id has no tests
     */
    public static List<FeatureTests> select(String ids, String skip) {
        List<FeatureTests> all = ALL.stream().map(tests -> tests.get()).toList();
        List<String> skipped = new ArrayList<>();
        for (String id : skip.split(",")) {
            if (!id.isBlank()) {
                skipped.add(find(all, id.trim()).id());
            }
        }
        List<FeatureTests> selected = new ArrayList<>();
        if (ids.trim().equalsIgnoreCase("all")) {
            selected.addAll(all);
        } else {
            for (String id : ids.split(",")) {
                selected.add(find(all, id.trim()));
            }
        }
        selected.removeIf(tests -> skipped.contains(tests.id()));
        return selected;
    }

    private static FeatureTests find(List<FeatureTests> all, String id) {
        return all.stream().filter(tests -> tests.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No dev tests for feature '" + id + "'; known: "
                + all.stream().map(known -> known.id()).toList()));
    }
}
