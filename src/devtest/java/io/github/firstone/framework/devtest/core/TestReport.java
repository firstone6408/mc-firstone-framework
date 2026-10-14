package io.github.firstone.framework.devtest.core;

import io.github.firstone.framework.FirstOneFramework;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Collects the results of a session and writes them to {@code results/}
 *
 * <ul>
 *   <li>{@code <feature>.md} — every test and check of a feature, with measured and expected values</li>
 *   <li>{@code summary.txt} — {@code PASS} or {@code FAIL} on the first line, read by the {@code devTest} task</li>
 * </ul>
 *
 * <p>Each result is also logged with the {@code [DEVTEST]} prefix.</p>
 */
final class TestReport {

    private final Path directory;
    private final List<Feature> features = new ArrayList<>();
    private final List<String> sessionErrors = new ArrayList<>();
    private int skipped;

    TestReport(Path directory) {
        this.directory = directory;
    }

    /** Deletes the results of the last run (the baselines are kept elsewhere) */
    void clearOldResults() {
        if (!Files.exists(this.directory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(this.directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        } catch (IOException e) {
            FirstOneFramework.LOGGER.error("[DEVTEST] Cannot delete the old results", e);
        }
    }

    void startFeature(String id) {
        this.features.add(new Feature(id, new ArrayList<>()));
        log("== " + id);
    }

    void startTest(String name) {
        currentFeature().tests().add(new Test(name, new ArrayList<>(), new long[] {System.nanoTime(), 0L}));
        log("-- " + name);
    }

    /**
     * Records a test left out in fast mode
     *
     * @param name the test's name
     */
    void skip(String name) {
        startTest(name);
        currentTest().rows().add(new Row("SKIP", "slow test", "left out in fast mode", ""));
        this.skipped++;
        endTest();
    }

    /** Records how long the current test took */
    void endTest() {
        long[] time = currentTest().time();
        time[1] = System.nanoTime() - time[0];
        log("   " + seconds(time[1]));
    }

    private static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.1f s", nanos / 1.0E9);
    }

    void check(String name, String actual, String expected, boolean passed) {
        currentTest().rows().add(new Row(passed ? "PASS" : "FAIL", name, actual, expected));
        log((passed ? "PASS " : "FAIL ") + name + ": " + actual + " (expected " + expected + ")");
    }

    void note(String name, String value) {
        currentTest().rows().add(new Row("NOTE", name, value, ""));
        log("NOTE " + name + ": " + value);
    }

    void fail(String message) {
        currentTest().rows().add(new Row("FAIL", "test stopped", message, ""));
        log("FAIL test stopped: " + message);
    }

    void error(Throwable error) {
        String text = stackTrace(error);
        if (this.features.isEmpty() || currentFeature().tests().isEmpty()) {
            this.sessionErrors.add(text);
        } else {
            currentTest().rows().add(new Row("FAIL", "error", error.toString(), ""));
        }
        FirstOneFramework.LOGGER.error("[DEVTEST] FAIL error", error);
    }

    void sessionError(Throwable error) {
        this.sessionErrors.add(stackTrace(error));
        FirstOneFramework.LOGGER.error("[DEVTEST] session error", error);
    }

    /** Writes the feature reports and the summary */
    void write() {
        int passed = 0;
        int failed = 0;
        try {
            Files.createDirectories(this.directory);
            for (Feature feature : this.features) {
                StringBuilder text = new StringBuilder();
                text.append("# Dev tests: ").append(feature.id()).append("\n\n");
                text.append("Run: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .append("\n\n");
                for (Test test : feature.tests()) {
                    boolean testPassed = test.rows().stream().noneMatch(row -> row.status().equals("FAIL"));
                    text.append("## ").append(testPassed ? "PASS" : "FAIL").append(" — ").append(test.name())
                        .append(" (").append(seconds(test.time()[1])).append(")")
                        .append("\n\n| | Check | Measured | Expected |\n|---|---|---|---|\n");
                    for (Row row : test.rows()) {
                        text.append("| ").append(row.status()).append(" | ").append(row.name()).append(" | ")
                            .append(row.actual()).append(" | ").append(row.expected()).append(" |\n");
                        if (row.status().equals("PASS")) {
                            passed++;
                        } else if (row.status().equals("FAIL")) {
                            failed++;
                        }
                    }
                    text.append('\n');
                }
                Files.writeString(this.directory.resolve(feature.id() + ".md"), text.toString());
            }
            failed += this.sessionErrors.size();
            String summary = (failed == 0 && passed > 0 ? "PASS" : "FAIL") + "\n"
                + "checks passed: " + passed + ", failed: " + failed
                + (this.skipped > 0 ? ", slow tests skipped (fast mode): " + this.skipped : "") + "\n"
                + "features: " + this.features.stream().map(feature -> feature.id()).toList() + "\n"
                + String.join("\n", this.sessionErrors);
            Files.writeString(this.directory.resolve("summary.txt"), summary);
            log("summary: " + summary.lines().findFirst().orElse("") + " (" + passed + " passed, " + failed
                + " failed)");
        } catch (IOException e) {
            FirstOneFramework.LOGGER.error("[DEVTEST] Cannot write the report", e);
        }
    }

    private Feature currentFeature() {
        return this.features.get(this.features.size() - 1);
    }

    private Test currentTest() {
        List<Test> tests = currentFeature().tests();
        if (tests.isEmpty()) {
            tests.add(new Test("(setup)", new ArrayList<>(), new long[] {System.nanoTime(), 0L}));
        }
        return tests.get(tests.size() - 1);
    }

    private static void log(String text) {
        FirstOneFramework.LOGGER.info("[DEVTEST] {}", text);
    }

    private static String stackTrace(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private record Feature(String id, List<Test> tests) {}

    /** A test: its name, its rows, and {start, duration} in nanoseconds */
    private record Test(String name, List<Row> rows, long[] time) {}

    private record Row(String status, String name, String actual, String expected) {}
}
