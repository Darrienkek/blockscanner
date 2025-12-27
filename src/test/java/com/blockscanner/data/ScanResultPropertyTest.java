package com.blockscanner.data;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for ScanResult data completeness.
 * 
 * Feature: block-scanner-mod, Property 4: ScanResult Data Completeness
 * Validates: Requirements 3.1
 */
class ScanResultPropertyTest {

    private static final String[] VALID_BLOCK_TYPES = {
        "barrier", "command_block", "chain_command_block", "repeating_command_block"
    };

    private static final String[] VALID_DIMENSIONS = {
        "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"
    };

    /**
     * Property 4: ScanResult Data Completeness
     * For any ScanResult added to the data store, it SHALL contain a non-null blockType,
     * valid x/y/z coordinates, and a non-null dimension string.
     * 
     * Validates: Requirements 3.1
     */
    @Property(tries = 100)
    void scanResultAlwaysHasCompleteData(
            @ForAll("validBlockTypes") String blockType,
            @ForAll @IntRange(min = -30000000, max = 30000000) int x,
            @ForAll @IntRange(min = -64, max = 320) int y,
            @ForAll @IntRange(min = -30000000, max = 30000000) int z,
            @ForAll("validDimensions") String dimension,
            @ForAll @LongRange(min = 0) long timestamp,
            @ForAll("optionalTexts") String signText
    ) {
        ScanResult result = new ScanResult(blockType, x, y, z, dimension, timestamp, signText);

        // Verify all fields are present and valid
        assertNotNull(result.blockType(), "blockType must not be null");
        assertFalse(result.blockType().isBlank(), "blockType must not be blank");
        assertNotNull(result.dimension(), "dimension must not be null");
        assertFalse(result.dimension().isBlank(), "dimension must not be blank");
        
        // Verify coordinates are accessible (records guarantee this, but we verify)
        assertEquals(x, result.x());
        assertEquals(y, result.y());
        assertEquals(z, result.z());
        assertEquals(timestamp, result.timestamp());
    }

    /**
     * Property: ScanResult rejects null blockType
     */
    @Property(tries = 100)
    void scanResultRejectsNullBlockType(
            @ForAll @IntRange(min = -30000000, max = 30000000) int x,
            @ForAll @IntRange(min = -64, max = 320) int y,
            @ForAll @IntRange(min = -30000000, max = 30000000) int z,
            @ForAll("validDimensions") String dimension,
            @ForAll @LongRange(min = 0) long timestamp
    ) {
        assertThrows(IllegalArgumentException.class, () -> 
            new ScanResult(null, x, y, z, dimension, timestamp, null)
        );
    }

    /**
     * Property: ScanResult rejects null dimension
     */
    @Property(tries = 100)
    void scanResultRejectsNullDimension(
            @ForAll("validBlockTypes") String blockType,
            @ForAll @IntRange(min = -30000000, max = 30000000) int x,
            @ForAll @IntRange(min = -64, max = 320) int y,
            @ForAll @IntRange(min = -30000000, max = 30000000) int z,
            @ForAll @LongRange(min = 0) long timestamp
    ) {
        assertThrows(IllegalArgumentException.class, () -> 
            new ScanResult(blockType, x, y, z, null, timestamp, null)
        );
    }

    /**
     * Property: ScanResult rejects blank blockType
     */
    @Property(tries = 100)
    void scanResultRejectsBlankBlockType(
            @ForAll("blankStrings") String blankBlockType,
            @ForAll @IntRange(min = -30000000, max = 30000000) int x,
            @ForAll @IntRange(min = -64, max = 320) int y,
            @ForAll @IntRange(min = -30000000, max = 30000000) int z,
            @ForAll("validDimensions") String dimension,
            @ForAll @LongRange(min = 0) long timestamp
    ) {
        assertThrows(IllegalArgumentException.class, () -> 
            new ScanResult(blankBlockType, x, y, z, dimension, timestamp, null)
        );
    }

    @Provide
    Arbitrary<String> validBlockTypes() {
        return Arbitraries.of(VALID_BLOCK_TYPES);
    }

    @Provide
    Arbitrary<String> validDimensions() {
        return Arbitraries.of(VALID_DIMENSIONS);
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", "   ", "\t", "\n", "  \t\n  ");
    }

    @Provide
    Arbitrary<String> optionalTexts() {
        return Arbitraries.oneOf(
            Arbitraries.just(null),
            Arbitraries.strings().withChars('a', 'z').ofMinLength(0).ofMaxLength(100)
        );
    }
}
