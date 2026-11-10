package dev.pikaid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeneratorTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);

    @Test
    void generationUsesTheCspRngBytesUnchanged() {
        byte[] entropy = HEX.parseHex("00112233445566778899aabb");
        PikaidGenerator generator = new PikaidGenerator(new FixedSecureRandom(entropy), FIXED_CLOCK);

        Pikaid generated = generator.generate();
        assertEquals("0s44we8002loz3po6mcf6vsn23", generated.toString());
        assertEquals("00112233445566778899aabb", generated.randomnessHex());
        assertEquals(1_700_000_000L, generated.timestamp());
    }

    @Test
    void generationRequestsExactlyTwelveEntropyBytes() {
        RecordingSecureRandom random = new RecordingSecureRandom();
        PikaidGenerator generator = new PikaidGenerator(random, FIXED_CLOCK);

        generator.generate();

        assertEquals(12, random.lastRequestedLength);
        assertEquals("abababababababababababab", generator.generate().randomnessHex());
    }

    @Test
    void explicitTimestampGenerationAndBinaryGenerationWork() {
        FixedSecureRandom random = new FixedSecureRandom(HEX.parseHex("00112233445566778899aabb"));
        PikaidGenerator generator = new PikaidGenerator(random, FIXED_CLOCK);

        Pikaid explicit = generator.generate(1_234_567_890L);
        assertEquals(1_234_567_890L, explicit.timestamp());
        assertEquals("0kf12oi002loz3po6mcf6vsn23", explicit.toString());

        byte[] binary = generator.generateBinary(1_700_000_000L);
        assertEquals("006553f10000112233445566778899aabb", HEX.formatHex(binary));
        assertEquals(17, binary.length);
    }

    @Test
    void generateBinaryUsesTheClock() {
        FixedSecureRandom random = new FixedSecureRandom(new byte[12]);
        PikaidGenerator generator = new PikaidGenerator(random, FIXED_CLOCK);

        byte[] binary = generator.generateBinary();

        assertEquals(17, binary.length);
        assertEquals("006553f100000000000000000000000000", HEX.formatHex(binary));
    }

    @Test
    void randomSourceFailuresPropagateAndNeverFallBack() {
        SecureRandom failing = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                throw new IllegalStateException("CSPRNG unavailable");
            }
        };
        PikaidGenerator generator = new PikaidGenerator(failing, FIXED_CLOCK);

        IllegalStateException fromClock = assertThrows(IllegalStateException.class, generator::generate);
        assertEquals("CSPRNG unavailable", fromClock.getMessage());

        IllegalStateException fromTimestamp = assertThrows(IllegalStateException.class,
                () -> generator.generate(1L));
        assertEquals("CSPRNG unavailable", fromTimestamp.getMessage());

        IllegalStateException fromBinary = assertThrows(IllegalStateException.class,
                generator::generateBinary);
        assertEquals("CSPRNG unavailable", fromBinary.getMessage());
    }

    @Test
    void clockOutsideRepresentableRangeFailsClosed() {
        PikaidGenerator beforeEpoch = new PikaidGenerator(new FixedSecureRandom(new byte[12]),
                Clock.fixed(Instant.ofEpochSecond(-1L), ZoneOffset.UTC));
        assertThrows(PikaidFormatException.class, beforeEpoch::generate);
        assertThrows(PikaidFormatException.class, beforeEpoch::generateBinary);

        PikaidGenerator afterMaximum = new PikaidGenerator(new FixedSecureRandom(new byte[12]),
                Clock.fixed(Instant.ofEpochSecond(Pikaid.MAX_TIMESTAMP + 1), ZoneOffset.UTC));
        assertThrows(PikaidFormatException.class, afterMaximum::generate);
    }

    @Test
    void explicitTimestampOutsideRepresentableRangeIsRejected() {
        PikaidGenerator generator = new PikaidGenerator(new FixedSecureRandom(new byte[12]), FIXED_CLOCK);
        assertThrows(PikaidFormatException.class, () -> generator.generate(-1L));
        assertThrows(PikaidFormatException.class, () -> generator.generate(Pikaid.MAX_TIMESTAMP + 1));
    }

    @Test
    void invalidTimestampsFailBeforeEntropyIsConsumed() {
        CountingSecureRandom random = new CountingSecureRandom();
        PikaidGenerator generator = new PikaidGenerator(random, FIXED_CLOCK);

        assertThrows(PikaidFormatException.class, () -> generator.generate(-1L));
        assertThrows(PikaidFormatException.class, () -> generator.generate(Pikaid.MAX_TIMESTAMP + 1));

        PikaidGenerator beforeEpoch = new PikaidGenerator(random,
                Clock.fixed(Instant.ofEpochSecond(-1L), ZoneOffset.UTC));
        PikaidGenerator afterMaximum = new PikaidGenerator(random,
                Clock.fixed(Instant.ofEpochSecond(Pikaid.MAX_TIMESTAMP + 1), ZoneOffset.UTC));
        assertThrows(PikaidFormatException.class, beforeEpoch::generate);
        assertThrows(PikaidFormatException.class, afterMaximum::generate);

        assertEquals(0, random.calls, "invalid timestamps must fail before consuming randomness");

        generator.generate(0L);
        assertEquals(1, random.calls, "valid generation consumes randomness exactly once");
    }

    @Test
    void boundaryTimestampsCanBeGenerated() {
        PikaidGenerator generator = new PikaidGenerator(new FixedSecureRandom(new byte[12]), FIXED_CLOCK);

        assertEquals(0L, generator.generate(0L).timestamp());
        assertEquals(Pikaid.MAX_TIMESTAMP, generator.generate(Pikaid.MAX_TIMESTAMP).timestamp());
    }

    @Test
    void singleArgumentConstructorUsesTheSystemClockAndSuppliedSource() {
        PikaidGenerator generator = new PikaidGenerator(new FixedSecureRandom(new byte[12]));

        long before = Instant.now().getEpochSecond();
        Pikaid generated = generator.generate();
        long after = Instant.now().getEpochSecond();

        assertTrue(generated.timestamp() >= before && generated.timestamp() <= after);
        assertEquals("000000000000000000000000", generated.randomnessHex());
    }

    @Test
    void constructorRejectsNullDependencies() {
        assertThrows(NullPointerException.class, () -> new PikaidGenerator((SecureRandom) null));
        assertThrows(NullPointerException.class, () -> new PikaidGenerator(new SecureRandom(), null));
    }

    @Test
    void generatedIdentifiersAreSaneAndVary() {
        PikaidGenerator generator = new PikaidGenerator(new SecureRandom(), FIXED_CLOCK);
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < 500; i++) {
            Pikaid id = generator.generate();
            assertEquals(1_700_000_000L, id.timestamp());
            assertTrue(Pikaid.isValid(id.toString()));
            assertEquals(id, Pikaid.fromBinary(id.toBinary()));
            assertEquals(id, Pikaid.parse(id.toString()));
            ids.add(id.toString());
        }

        assertTrue(ids.size() > 1, "500 CSPRNG identifiers should not all be identical");
    }

    @Test
    void staticGenerationProducesAValidIdentifier() {
        long before = Instant.now().getEpochSecond();
        Pikaid id = Pikaid.generate();
        long after = Instant.now().getEpochSecond();

        assertTrue(Pikaid.isValid(id.toString()));
        assertTrue(id.timestamp() >= before && id.timestamp() <= after,
                "generated timestamp should come from the current clock");

        Pikaid fixed = Pikaid.generate(1_700_000_000L);
        assertEquals(1_700_000_000L, fixed.timestamp());
        assertEquals(17, Pikaid.generateBinary().length);
    }

    private static final class FixedSecureRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;
        private final byte[] bytes;

        FixedSecureRandom(byte[] bytes) {
            this.bytes = bytes.clone();
        }

        @Override
        public void nextBytes(byte[] output) {
            System.arraycopy(bytes, 0, output, 0, output.length);
        }
    }

    private static final class RecordingSecureRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;
        private int lastRequestedLength = -1;

        @Override
        public void nextBytes(byte[] output) {
            lastRequestedLength = output.length;
            Arrays.fill(output, (byte) 0xAB);
        }
    }

    private static final class CountingSecureRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;
        private int calls;

        @Override
        public void nextBytes(byte[] output) {
            calls++;
            Arrays.fill(output, (byte) 0);
        }
    }
}
