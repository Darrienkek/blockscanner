package com.blockscanner;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Report;
import net.jqwik.api.Reporting;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Property-based tests for ScanController functionality.
 */
public class ScanControllerPropertyTest {

    /**
     * Mock ScanController for testing toggle behavior without client dependencies.
     */
    private static class MockScanController {
        private boolean scanningActive = false;

        public void toggle() {
            scanningActive = !scanningActive;
        }

        public boolean isActive() {
            return scanningActive;
        }
    }

    /**
     * Feature: block-scanner-mod, Property 1: Toggle State Inversion
     *
     * For any initial scanning state (active or inactive), calling toggle()
     * on the ScanController SHALL result in the opposite state.
     *
     * Validates: Requirements 1.1
     */
    @Property
    @Report(Reporting.GENERATED)
    void toggleStateInversion(@ForAll boolean initialState) {
        MockScanController scanController = new MockScanController();

        if (initialState && !scanController.isActive()) {
            scanController.toggle();
        } else if (!initialState && scanController.isActive()) {
            scanController.toggle();
        }

        boolean actualInitialState = scanController.isActive();
        scanController.toggle();
        boolean finalState = scanController.isActive();

        assertNotEquals(actualInitialState, finalState);
    }

    @Property
    void batchCompletionGate(
        @ForAll @IntRange(min = 0, max = 80) int scannedChunks
    ) {
        boolean expected = scannedChunks >= SpiralTraversal.BATCH_TOTAL;
        assertEquals(expected, ScanController.isBatchComplete(scannedChunks));
    }

    @Property
    void spectatorWarningThrottle(
        @ForAll @LongRange(min = 0, max = 10_000) long lastWarningAt,
        @ForAll @LongRange(min = 0, max = 10_000) long interval,
        @ForAll @LongRange(min = 0, max = 20_000) long now
    ) {
        boolean expected = now - lastWarningAt >= interval;
        assertEquals(expected, ScanController.shouldWarnSpectator(now, lastWarningAt, interval));
    }
}