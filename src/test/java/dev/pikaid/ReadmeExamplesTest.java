package dev.pikaid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * Mirrors the usage examples published in the README as executable tests, so a
 * change to the documented API surface breaks the build.
 */
class ReadmeExamplesTest {

    @Test
    void quickStartExampleCompilesAndRuns() {
        Pikaid id = Pikaid.generate();

        String text = id.toString();
        long timestamp = id.timestamp();
        String randomnessHex = id.randomnessHex();
        byte[] binary = id.toBinary();

        assertTrue(Pikaid.isValid(text));
        assertEquals(26, text.length());
        assertEquals(17, binary.length);
        assertEquals(24, randomnessHex.length());
        assertTrue(timestamp >= 0 && timestamp <= Pikaid.MAX_TIMESTAMP);

        assertEquals(id, Pikaid.parse(text));
        assertEquals(id, Pikaid.fromBinary(binary));
        assertEquals(text, Pikaid.parse(text).toString());
        assertArrayEquals(binary, Pikaid.fromBinary(binary).toBinary());
    }

    @Test
    void explicitTimestampAndDeterministicExamplesMatchTheFixture() {
        Pikaid fixed = Pikaid.generate(1_700_000_000L);
        assertEquals(1_700_000_000L, fixed.timestamp());

        Pikaid known = Pikaid.of(1_700_000_000L,
                HexFormat.of().parseHex("00112233445566778899aabb"));
        assertEquals("0s44we8002loz3po6mcf6vsn23", known.toString());
        assertEquals("006553f10000112233445566778899aabb",
                HexFormat.of().formatHex(known.toBinary()));
    }

    @Test
    void generatorExampleCompilesAndRuns() {
        PikaidGenerator generator = new PikaidGenerator();

        Pikaid id = generator.generate();
        byte[] binary = generator.generateBinary();

        assertTrue(Pikaid.isValid(id.toString()));
        assertEquals(17, binary.length);
        assertEquals(id, Pikaid.fromBinary(id.toBinary()));
    }

    @Test
    void validationExampleDistinguishesShapeFromSemantics() {
        String candidate = "0000000zzzzzzzzzzzzzzzzzzz";

        assertTrue(Pikaid.isWellFormed(candidate));
        assertEquals(false, Pikaid.isValid(candidate));
        assertEquals(false, Pikaid.isValid("not-a-pikaid"));
    }
}
