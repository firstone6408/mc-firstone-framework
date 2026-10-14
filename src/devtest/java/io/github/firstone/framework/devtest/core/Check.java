package io.github.firstone.framework.devtest.core;

import java.util.Locale;
import java.util.Objects;

/**
 * One check of a test: a measured value and what it must be
 *
 * <p>Made by {@link TestContext#check}; call one of the methods to record the result in the report. A failed check
 * does not stop the test, so a run shows every difference at once.</p>
 */
public final class Check {

    private final TestReport report;
    private final String name;
    private final Object actual;

    Check(TestReport report, String name, Object actual) {
        this.report = report;
        this.name = name;
        this.actual = actual;
    }

    /**
     * The value must be within {@code tolerance} of {@code expected}
     *
     * @param expected  expected value
     * @param tolerance allowed difference
     */
    public void near(double expected, double tolerance) {
        record(Math.abs(number() - expected) <= tolerance, format(expected) + " ± " + format(tolerance));
    }

    /**
     * The value must be from {@code min} to {@code max}
     *
     * @param min lowest allowed value
     * @param max highest allowed value
     */
    public void between(double min, double max) {
        double value = number();
        record(value >= min && value <= max, format(min) + " .. " + format(max));
    }

    /**
     * The value must be at least {@code min}
     *
     * @param min lowest allowed value
     */
    public void atLeast(double min) {
        record(number() >= min, ">= " + format(min));
    }

    /**
     * The value must be at most {@code max}
     *
     * @param max highest allowed value
     */
    public void atMost(double max) {
        record(number() <= max, "<= " + format(max));
    }

    /** The value must be {@code true} */
    public void isTrue() {
        record(Boolean.TRUE.equals(this.actual), "true");
    }

    /** The value must be {@code false} */
    public void isFalse() {
        record(Boolean.FALSE.equals(this.actual), "false");
    }

    /**
     * The value must equal {@code expected}
     *
     * @param expected expected value
     */
    public void isEqualTo(Object expected) {
        record(Objects.equals(this.actual, expected), String.valueOf(expected));
    }

    private double number() {
        return this.actual instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private void record(boolean passed, String expected) {
        String shown = this.actual instanceof Double || this.actual instanceof Float
            ? format(((Number) this.actual).doubleValue())
            : String.valueOf(this.actual);
        this.report.check(this.name, shown, expected, passed);
    }

    /** Formats a number with 3 decimals, the precision boat speeds are compared at */
    static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
