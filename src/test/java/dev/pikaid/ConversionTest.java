package dev.pikaid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class ConversionTest {

    private static final HexFormat HEX = HexFormat.of();

    @Test
    void constantsMatchTheCanonicalFormat() {
        assertEquals(26, Pikaid.STRING_LENGTH);
        assertEquals(17, Pikaid.BINARY_LENGTH);
        assertEquals(7, Pikaid.TIMESTAMP_CHARS);
        assertEquals(19, Pikaid.RANDOMNESS_CHARS);
        assertEquals(12, Pikaid.RANDOMNESS_BYTES);
        assertEquals(0L, Pikaid.MIN_TIMESTAMP);
        assertEquals(78_364_164_095L, Pikaid.MAX_TIMESTAMP);
    }

    @Test
    void binaryLayoutIsFiveByteTimestampThenTwelveEntropyBytes() {
        Pikaid id = Pikaid.of(1_700_000_000L, HEX.parseHex("00112233445566778899aabb"));
        assertEquals("006553f10000112233445566778899aabb", HEX.formatHex(id.toBinary()));
        assertEquals(17, id.toBinary().length);
    }

    @Test
    void leadingZeroEntropyIsPreservedExactly() {
        byte[] entropy = new byte[12];
        entropy[11] = 1;
        Pikaid id = Pikaid.of(1L, entropy);

        assertEquals("000000000000000000000001", id.randomnessHex());
        assertEquals("00000010000000000000000001", id.toString());
        assertEquals("0000000001000000000000000000000001", HEX.formatHex(id.toBinary()));
        assertEquals(id, Pikaid.fromBinary(id.toBinary()));
        assertEquals(id, Pikaid.parse(id.toString()));
    }

    @Test
    void binaryTimestampUsesAllFiveBytesWithoutSignIssues() {
        Pikaid id = Pikaid.of(Pikaid.MAX_TIMESTAMP, new byte[12]);
        byte[] binary = id.toBinary();
        assertEquals(0x12, binary[0] & 0xFF);
        assertEquals(0x3E, binary[1] & 0xFF);
        assertEquals(0xDE, binary[2] & 0xFF);
        assertEquals(0x3F, binary[3] & 0xFF);
        assertEquals(0xFF, binary[4] & 0xFF);
        assertEquals(Pikaid.MAX_TIMESTAMP, Pikaid.fromBinary(binary).timestamp());
    }

    @Test
    void defensiveCopiesProtectImmutability() {
        byte[] entropy = HEX.parseHex("00112233445566778899aabb");
        Pikaid id = Pikaid.of(1_700_000_000L, entropy);

        entropy[0] = (byte) 0xFF;
        assertEquals("00112233445566778899aabb", id.randomnessHex());

        byte[] returnedEntropy = id.randomness();
        returnedEntropy[0] = (byte) 0xFF;
        assertEquals("00112233445566778899aabb", id.randomnessHex());

        byte[] returnedBinary = id.toBinary();
        returnedBinary[0] = 0;
        assertEquals("006553f10000112233445566778899aabb", HEX.formatHex(id.toBinary()));
    }

    @Test
    void factoryRejectsInvalidFields() {
        assertThrows(PikaidFormatException.class, () -> Pikaid.of(0L, null));
        assertThrows(PikaidFormatException.class, () -> Pikaid.of(0L, new byte[11]));
        assertThrows(PikaidFormatException.class, () -> Pikaid.of(0L, new byte[13]));
        assertThrows(PikaidFormatException.class, () -> Pikaid.of(-1L, new byte[12]));
        assertThrows(PikaidFormatException.class, () -> Pikaid.of(Pikaid.MAX_TIMESTAMP + 1, new byte[12]));
    }

    @Test
    void binaryDecoderRejectsInvalidLengthAndTimestampOverflow() {
        assertThrows(PikaidFormatException.class, () -> Pikaid.fromBinary(null));
        assertThrows(PikaidFormatException.class, () -> Pikaid.fromBinary(new byte[16]));
        assertThrows(PikaidFormatException.class, () -> Pikaid.fromBinary(new byte[18]));

        byte[] maxPlusOne = HEX.parseHex("123ede40" + "00".repeat(12));
        assertThrows(PikaidFormatException.class, () -> Pikaid.fromBinary(maxPlusOne));

        byte[] fortyBitMax = HEX.parseHex("ffffffffff" + "00".repeat(12));
        assertThrows(PikaidFormatException.class, () -> Pikaid.fromBinary(fortyBitMax));

        byte[] lastValid = HEX.parseHex("123ede3fff" + "00".repeat(12));
        assertEquals(Pikaid.MAX_TIMESTAMP, Pikaid.fromBinary(lastValid).timestamp());
    }

    @Test
    void timestampInstantIsUtcAndPrecise() {
        assertEquals(Instant.EPOCH, Pikaid.of(0L, new byte[12]).timestampInstant());
        assertEquals(Instant.ofEpochSecond(1_700_000_000L),
                Pikaid.of(1_700_000_000L, new byte[12]).timestampInstant());
        assertEquals(Instant.parse("4453-04-05T15:21:35Z"),
                Pikaid.of(Pikaid.MAX_TIMESTAMP, new byte[12]).timestampInstant());
    }

    @Test
    void equalityAndHashUseBothFields() {
        Pikaid base = Pikaid.of(1_700_000_000L, HEX.parseHex("00112233445566778899aabb"));
        Pikaid same = Pikaid.of(1_700_000_000L, HEX.parseHex("00112233445566778899aabb"));
        Pikaid otherTimestamp = Pikaid.of(1_700_000_001L, HEX.parseHex("00112233445566778899aabb"));
        Pikaid otherEntropy = Pikaid.of(1_700_000_000L, HEX.parseHex("00112233445566778899aabc"));

        assertTrue(base.equals(base));
        assertEquals(base, same);
        assertEquals(base.hashCode(), same.hashCode());
        assertNotEquals(base, otherTimestamp);
        assertNotEquals(base, otherEntropy);
        assertNotEquals(base, null);
        assertNotEquals(base, base.toString());
    }

    @Test
    void compareToRejectsNull() {
        Pikaid id = Pikaid.of(0L, new byte[12]);
        assertThrows(NullPointerException.class, () -> id.compareTo(null));
    }

    @Test
    void randomnessHexIsLowercaseAndFixedWidth() {
        Pikaid id = Pikaid.of(0L, new byte[12]);
        assertEquals("000000000000000000000000", id.randomnessHex());
        assertEquals(24, id.randomnessHex().length());
        assertTrue(id.randomnessHex().matches("[0-9a-f]{24}"));
    }
}
