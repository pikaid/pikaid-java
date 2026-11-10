package dev.pikaid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OrderingTest {

    @Test
    void differentTimestampsSortChronologicallyInTextAndBinary() {
        long[] timestamps = {0L, 1L, 1_295L, 1_296L, 1_700_000_000L, Pikaid.MAX_TIMESTAMP - 1, Pikaid.MAX_TIMESTAMP};
        // Deliberately descending entropy so the suffix cannot mask timestamp order.
        List<Pikaid> ids = new ArrayList<>();
        for (int i = 0; i < timestamps.length; i++) {
            byte[] entropy = new byte[12];
            Arrays.fill(entropy, (byte) (0xFF - i));
            ids.add(Pikaid.of(timestamps[i], entropy));
        }

        for (int i = 0; i + 1 < ids.size(); i++) {
            Pikaid earlier = ids.get(i);
            Pikaid later = ids.get(i + 1);
            assertTrue(earlier.timestamp() < later.timestamp());
            assertTrue(earlier.toString().compareTo(later.toString()) < 0);
            assertTrue(Arrays.compareUnsigned(earlier.toBinary(), later.toBinary()) < 0);
            assertTrue(earlier.compareTo(later) < 0);
            assertTrue(later.compareTo(earlier) > 0);
        }
    }

    @Test
    void sameTimestampOrderingUsesUnsignedSuffixNotGenerationOrder() {
        long timestamp = 1_700_000_000L;
        // Constructed in an order unrelated to the suffix order.
        Pikaid high = Pikaid.of(timestamp, entropyWithLastByte(9));
        Pikaid low = Pikaid.of(timestamp, entropyWithLastByte(1));
        Pikaid middle = Pikaid.of(timestamp, entropyWithLastByte(5));

        List<Pikaid> generatedOrder = List.of(high, low, middle);
        List<Pikaid> sorted = new ArrayList<>(generatedOrder);
        sorted.sort(Comparator.naturalOrder());

        assertEquals(List.of(low, middle, high), sorted);
        assertTrue(low.toString().compareTo(middle.toString()) < 0);
        assertTrue(middle.toString().compareTo(high.toString()) < 0);
        assertTrue(Arrays.compareUnsigned(low.toBinary(), middle.toBinary()) < 0);
    }

    @Test
    void signedByteValuesDoNotBreakBinaryOrdering() {
        Pikaid low = Pikaid.of(1L, entropyWithLastByte(0x7F));
        Pikaid high = Pikaid.of(1L, entropyWithLastByte(0x80));

        assertTrue(low.toString().compareTo(high.toString()) < 0);
        assertTrue(Arrays.compareUnsigned(low.toBinary(), high.toBinary()) < 0);
        assertTrue(low.compareTo(high) < 0);
    }

    @Test
    void textBinaryAndValueOrderAgreeForRandomPairs() {
        Random random = new Random(0x5EED_1234_ABCDL);
        for (int i = 0; i < 2_000; i++) {
            long firstTimestamp = random.nextLong(Pikaid.MAX_TIMESTAMP + 1);
            long secondTimestamp = random.nextLong(Pikaid.MAX_TIMESTAMP + 1);
            byte[] firstEntropy = randomBytes(random);
            byte[] secondEntropy = randomBytes(random);

            Pikaid first = Pikaid.of(firstTimestamp, firstEntropy);
            Pikaid second = Pikaid.of(secondTimestamp, secondEntropy);

            int expected = Long.compare(firstTimestamp, secondTimestamp);
            if (expected == 0) {
                expected = Arrays.compareUnsigned(firstEntropy, secondEntropy);
            }

            assertEquals(Integer.signum(expected), Integer.signum(first.compareTo(second)),
                    "value order at iteration " + i);
            assertEquals(Integer.signum(first.toString().compareTo(second.toString())),
                    Integer.signum(first.compareTo(second)), "text order at iteration " + i);
            assertEquals(Integer.signum(Arrays.compareUnsigned(first.toBinary(), second.toBinary())),
                    Integer.signum(first.compareTo(second)), "binary order at iteration " + i);
        }
    }

    @Test
    void equalValuesCompareAsZeroAndRoundTripBinary() {
        Pikaid first = Pikaid.of(42L, entropyWithLastByte(7));
        Pikaid second = Pikaid.parse(first.toString());

        assertEquals(0, first.compareTo(second));
        assertArrayEquals(first.toBinary(), second.toBinary());
        assertEquals(first, second);
    }

    private static byte[] entropyWithLastByte(int value) {
        byte[] entropy = new byte[12];
        entropy[11] = (byte) value;
        return entropy;
    }

    private static byte[] randomBytes(Random random) {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return bytes;
    }
}
