package io.github.firstone.framework.devtest.core;

import io.github.firstone.framework.FirstOneFramework;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;

import java.nio.file.Path;
import java.util.List;

/**
 * One run of the dev tests: for each feature, creates its world, runs its tests and writes the report, then closes
 * the game
 *
 * <p>The tests run on their own thread, in step with the client ticks ({@link TickBridge}).</p>
 */
public final class TestSession {


    private final List<FeatureTests> features;
    private final String nameFilter;
    private final boolean fast;
    private final boolean updateBaselines;
    private final TickBridge bridge = new TickBridge();
    private Thread thread;

    /**
     * Creates a session
     *
     * @param features        the features to test, in order
     * @param nameFilter      run only the tests whose name contains this text (empty: all)
     * @param fast            skip the tests marked slow ({@link TestCase#slow})
     * @param updateBaselines replace the baseline screenshots with the new ones
     */
    public TestSession(List<FeatureTests> features, String nameFilter, boolean fast, boolean updateBaselines) {
        this.features = features;
        this.nameFilter = nameFilter.trim();
        this.fast = fast;
        this.updateBaselines = updateBaselines;
    }

    /**
     * Called at the end of every client tick: starts the tests once the game has loaded, then hands each tick to them
     *
     * @param minecraft the game
     */
    public void onClientTick(Minecraft minecraft) {
        if (this.thread == null) {
            if (minecraft.getOverlay() != null || minecraft.screen == null) {
                return;
            }
            this.thread = new Thread(() -> runAll(minecraft), "FirstOne DevTest");
            this.thread.setDaemon(true);
            this.thread.start();
        }
        if (!this.bridge.onClientTick()) {
            FirstOneFramework.LOGGER.error("[DEVTEST] The tests stopped answering; closing the game");
            minecraft.stop();
        }
    }

    /** Runs every feature's tests (test thread) */
    private void runAll(Minecraft minecraft) {
        this.bridge.awaitFirstTurn();
        Path results = minecraft.gameDirectory.toPath().resolve("results");
        Path baselines = minecraft.gameDirectory.toPath().resolve("baselines");
        TestReport report = new TestReport(results);
        report.clearOldResults();
        try {
            this.bridge.onClientThread(() -> {
                // tests must keep running while the window is in the background
                minecraft.options.pauseOnLostFocus = false;
                minecraft.options.renderDistance().set(10);
                minecraft.options.simulationDistance().set(10);
                // no tutorial hints in the corner of the screen
                minecraft.getTutorial().setStep(TutorialSteps.NONE);
                return null;
            });
            for (FeatureTests feature : this.features) {
                TestContext t = new TestContext(minecraft, this.bridge, report, feature.id(),
                    new Screenshots(results.resolve("screenshots").resolve(feature.id()),
                        baselines.resolve(feature.id()), this.updateBaselines));
                runFeature(t, feature, report);
            }
        } catch (Throwable e) {
            report.sessionError(e);
        }
        report.write();
        this.bridge.onClientThread(() -> {
            minecraft.stop();
            return null;
        });
        this.bridge.close();
    }

    /** Runs one feature's tests in its own new world */
    private void runFeature(TestContext t, FeatureTests feature, TestReport report) {
        report.startFeature(feature.id());
        List<Object> originals = Configs.copyAll(feature.configs());
        try {
            TestWorld.create(t, "devtest_" + feature.id());
            for (TestCase test : feature.tests()) {
                if (!test.name().contains(this.nameFilter)) {
                    continue;
                }
                if (this.fast && test.slow()) {
                    report.skip(test.name());
                    continue;
                }
                report.startTest(test.name());
                try {
                    Configs.resetAll(feature.configs());
                    t.resetForTest();
                    test.body().run(t);
                } catch (TestFailure e) {
                    report.fail(e.getMessage());
                } catch (Throwable e) {
                    report.error(e);
                } finally {
                    t.cleanUpAfterTest();
                    report.endTest();
                }
            }
        } catch (Throwable e) {
            report.error(e);
        } finally {
            Configs.restoreAll(feature.configs(), originals);
            TestWorld.leave(t);
        }
    }
}
