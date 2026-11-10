package dev.pikaid;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable pikaid value.
 *
 * <p>A pikaid is exactly 26 lowercase ASCII Base36 characters: seven characters
 * for an unsigned Unix timestamp in whole seconds and nineteen characters for
 * exactly 96 bits of randomness. The equivalent binary representation is
 * exactly 17 bytes: five unsigned big-endian timestamp bytes followed by the
 * twelve randomness bytes unchanged.</p>
 *
 * <p>Use {@link #generate()} or {@link PikaidGenerator} for normal generation.
 * Use {@link #parse(String)} and {@link #fromBinary(byte[])} for strict
 * decoding; both reject invalid input with {@link PikaidFormatException} rather
 * than normalizing or truncating it. {@link #of(long, byte[])} is a
 * deterministic factory intended for interoperability fixtures and tests, not
 * for normal production generation.</p>
 *
 * <p>For two valid identifiers with different timestamps, text and binary
 * comparison order matches chronological order under ASCII-compatible or
 * unsigned bytewise comparison. Identifiers with the same timestamp are ordered
 * by their random suffix, <strong>not</strong> by generation order. The
 * timestamp has one-second precision. pikaid is not a monotonic sequence, an
 * authentication token, or a secret.</p>
 *
 * <p>Instances are immutable and thread-safe.</p>
 */
public final class Pikaid implements Comparable<Pikaid> {

    /** Number of characters in the canonical text representation: {@value}. */
    public static final int STRING_LENGTH = 26;

    /** Number of bytes in the canonical binary representation: {@value}. */
    public static final int BINARY_LENGTH = 17;

    /** Number of Base36 characters used by the timestamp: {@value}. */
    public static final int TIMESTAMP_CHARS = 7;

    /** Number of Base36 characters used by the randomness suffix: {@value}. */
    public static final int RANDOMNESS_CHARS = 19;

    /** Number of randomness bytes: {@value}. */
    public static final int RANDOMNESS_BYTES = 12;

    /** Smallest representable timestamp, the Unix epoch: {@value}. */
    public static final long MIN_TIMESTAMP = 0L;

    /**
     * Largest representable timestamp, {@code 36^7 - 1}: {@value}.
     */
    public static final long MAX_TIMESTAMP = 78_364_164_095L;

    private static final BigInteger MAX_RANDOMNESS =
            BigInteger.ONE.shiftLeft(96).subtract(BigInteger.ONE);
    private static final BigInteger BASE = BigInteger.valueOf(36);
    private static final char[] BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int[] DIGIT_VALUES = new int[128];

    static {
        Arrays.fill(DIGIT_VALUES, -1);
        for (int i = 0; i < BASE36.length; i++) {
            DIGIT_VALUES[BASE36[i]] = i;
        }
    }

    private final long timestamp;
    private final byte[] randomness;
    private final String text;
    private final int hash;

    private Pikaid(long timestamp, byte[] randomness) {
        if (timestamp < MIN_TIMESTAMP || timestamp > MAX_TIMESTAMP) {
            throw new PikaidFormatException(
                    "timestamp out of range: " + timestamp + " (expected 0.." + MAX_TIMESTAMP + ")");
        }
        if (randomness == null) {
            throw new PikaidFormatException("randomness must not be null");
        }
        if (randomness.length != RANDOMNESS_BYTES) {
            throw new PikaidFormatException(
                    "randomness must be exactly " + RANDOMNESS_BYTES + " bytes, was " + randomness.length);
        }
        this.timestamp = timestamp;
        this.randomness = randomness.clone();
        this.text = encodeTimestamp(timestamp) + encodeRandomness(this.randomness);
        this.hash = 31 * Long.hashCode(timestamp) + Arrays.hashCode(this.randomness);
    }

    /**
     * Generates a pikaid using the current UTC Unix time and a cryptographically
     * secure random source.
     *
     * @return a new valid pikaid
     * @throws PikaidFormatException if the system clock is outside the
     *                               representable timestamp range
     * @throws RuntimeException      if the cryptographic random source fails;
     *                               generation fails closed and never falls back
     */
    public static Pikaid generate() {
        return DefaultGeneratorHolder.INSTANCE.generate();
    }

    /**
     * Generates a pikaid with an explicitly supplied timestamp and a
     * cryptographically secure random source.
     *
     * @param timestamp unsigned Unix seconds in {@code 0..78,364,164,095}
     * @return a new valid pikaid
     * @throws PikaidFormatException if the timestamp is out of range
     * @throws RuntimeException      if the cryptographic random source fails;
     *                               generation fails closed and never falls back
     */
    public static Pikaid generate(long timestamp) {
        return DefaultGeneratorHolder.INSTANCE.generate(timestamp);
    }

    /**
     * Generates a pikaid and returns its canonical 17-byte binary form.
     *
     * @return a new 17-byte binary pikaid
     * @throws PikaidFormatException if the system clock is outside the
     *                               representable timestamp range
     * @throws RuntimeException      if the cryptographic random source fails
     */
    public static byte[] generateBinary() {
        return DefaultGeneratorHolder.INSTANCE.generateBinary();
    }

    /**
     * Creates a pikaid from a timestamp and exactly twelve randomness bytes.
     *
     * <p>This deterministic factory is intended for known-answer fixtures,
     * interoperability tests, and decoding pipelines that already hold raw
     * randomness. Normal generation must use {@link #generate()} or
     * {@link PikaidGenerator}, which obtain randomness from a CSPRNG.</p>
     *
     * @param timestamp  unsigned Unix seconds in {@code 0..78,364,164,095}
     * @param randomness exactly twelve bytes, used unchanged
     * @return a pikaid with the supplied fields
     * @throws PikaidFormatException if the timestamp is out of range or the
     *                               randomness length is not twelve bytes
     */
    public static Pikaid of(long timestamp, byte[] randomness) {
        return new Pikaid(timestamp, randomness);
    }

    /**
     * Strictly parses a 26-character pikaid string.
     *
     * <p>The whole string must be lowercase ASCII Base36, its timestamp must be
     * representable, and its randomness must fit in 96 bits. No Unicode
     * normalization, case folding, trimming, or truncation is performed.</p>
     *
     * @param value candidate text, not {@code null}
     * @return the decoded pikaid
     * @throws PikaidFormatException if the value is not a valid pikaid
     */
    public static Pikaid parse(String value) {
        if (value == null) {
            throw new PikaidFormatException("pikaid string must not be null");
        }
        if (value.length() != STRING_LENGTH) {
            throw new PikaidFormatException(
                    "pikaid string must be exactly " + STRING_LENGTH + " characters, was " + value.length());
        }
        for (int i = 0; i < STRING_LENGTH; i++) {
            char c = value.charAt(i);
            if (c >= DIGIT_VALUES.length || DIGIT_VALUES[c] < 0) {
                throw new PikaidFormatException(
                        "invalid character at index " + i
                                + ": pikaid must contain only lowercase ASCII [0-9a-z]");
            }
        }

        long timestamp = decodeTimestamp(value, 0, TIMESTAMP_CHARS);
        BigInteger randomnessValue = decodeBase36(value, TIMESTAMP_CHARS, RANDOMNESS_CHARS);
        if (randomnessValue.compareTo(MAX_RANDOMNESS) > 0) {
            throw new PikaidFormatException("randomness exceeds 96 bits");
        }
        return new Pikaid(timestamp, toFixedWidthBytes(randomnessValue, RANDOMNESS_BYTES));
    }

    /**
     * Strictly decodes a 17-byte binary pikaid.
     *
     * @param binary candidate bytes, not {@code null}
     * @return the decoded pikaid
     * @throws PikaidFormatException if the length is not 17 bytes or the
     *                               timestamp exceeds {@link #MAX_TIMESTAMP}
     */
    public static Pikaid fromBinary(byte[] binary) {
        if (binary == null) {
            throw new PikaidFormatException("binary pikaid must not be null");
        }
        if (binary.length != BINARY_LENGTH) {
            throw new PikaidFormatException(
                    "binary pikaid must be exactly " + BINARY_LENGTH + " bytes, was " + binary.length);
        }

        long timestamp = 0L;
        for (int i = 0; i < 5; i++) {
            timestamp = (timestamp << 8) | (binary[i] & 0xFFL);
        }
        if (timestamp > MAX_TIMESTAMP) {
            throw new PikaidFormatException(
                    "binary timestamp exceeds maximum " + MAX_TIMESTAMP + ": " + timestamp);
        }

        byte[] randomness = Arrays.copyOfRange(binary, 5, BINARY_LENGTH);
        return new Pikaid(timestamp, randomness);
    }

    /**
     * Reports whether a string is a fully valid pikaid.
     *
     * <p>This performs complete semantic validation, including the 96-bit
     * randomness limit. It returns {@code false} for {@code null} and any
     * invalid value.</p>
     *
     * @param value candidate text, possibly {@code null}
     * @return {@code true} only if the value is fully valid
     */
    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        try {
            parse(value);
            return true;
        } catch (PikaidFormatException expected) {
            return false;
        }
    }

    /**
     * Reports whether a string has the right length and alphabet.
     *
     * <p>This is a shape-only check. It does <strong>not</strong> verify the
     * 96-bit randomness limit and must not be described as full validity. Use
     * {@link #isValid(String)} for semantic validation.</p>
     *
     * @param value candidate text, possibly {@code null}
     * @return {@code true} if the value is 26 lowercase ASCII Base36 characters
     */
    public static boolean isWellFormed(String value) {
        if (value == null || value.length() != STRING_LENGTH) {
            return false;
        }
        for (int i = 0; i < STRING_LENGTH; i++) {
            char c = value.charAt(i);
            if (c >= DIGIT_VALUES.length || DIGIT_VALUES[c] < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the unsigned Unix timestamp in whole seconds.
     *
     * @return timestamp in {@code 0..78,364,164,095}
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * Returns the timestamp as a UTC {@link Instant}.
     *
     * @return the timestamp as a UTC instant
     */
    public Instant timestampInstant() {
        return Instant.ofEpochSecond(timestamp);
    }

    /**
     * Returns a defensive copy of the twelve randomness bytes.
     *
     * @return a new twelve-byte array
     */
    public byte[] randomness() {
        return randomness.clone();
    }

    /**
     * Returns the randomness as twenty-four lowercase hexadecimal characters.
     *
     * @return lowercase hexadecimal representation of the twelve randomness bytes
     */
    public String randomnessHex() {
        char[] out = new char[RANDOMNESS_BYTES * 2];
        for (int i = 0; i < RANDOMNESS_BYTES; i++) {
            int value = randomness[i] & 0xFF;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(out);
    }

    /**
     * Returns a new 17-byte canonical binary representation.
     *
     * @return five big-endian timestamp bytes followed by twelve randomness bytes
     */
    public byte[] toBinary() {
        byte[] out = new byte[BINARY_LENGTH];
        out[0] = (byte) (timestamp >>> 32);
        out[1] = (byte) (timestamp >>> 24);
        out[2] = (byte) (timestamp >>> 16);
        out[3] = (byte) (timestamp >>> 8);
        out[4] = (byte) timestamp;
        System.arraycopy(randomness, 0, out, 5, RANDOMNESS_BYTES);
        return out;
    }

    /**
     * Compares this pikaid with another.
     *
     * <p>Comparison is by timestamp first, then by unsigned lexicographic order
     * of the randomness bytes. This matches ASCII-compatible comparison of the
     * text form and unsigned bytewise comparison of the binary form. For equal
     * timestamps the order is by random suffix, not generation order.</p>
     *
     * @param other the other pikaid, not {@code null}
     * @return a negative value, zero, or a positive value as this value is less
     *         than, equal to, or greater than {@code other}
     */
    @Override
    public int compareTo(Pikaid other) {
        Objects.requireNonNull(other, "other");
        int timestampOrder = Long.compare(timestamp, other.timestamp);
        if (timestampOrder != 0) {
            return timestampOrder;
        }
        return Arrays.compareUnsigned(randomness, other.randomness);
    }

    /**
     * Returns the canonical 26-character text representation.
     *
     * @return lowercase Base36 pikaid text
     */
    @Override
    public String toString() {
        return text;
    }

    /**
     * Compares this pikaid with another for equality of both fields.
     *
     * @param obj the object to compare
     * @return {@code true} if both represent the same timestamp and randomness
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Pikaid other)) {
            return false;
        }
        return timestamp == other.timestamp && Arrays.equals(randomness, other.randomness);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return hash;
    }

    private static String encodeTimestamp(long value) {
        char[] chars = new char[TIMESTAMP_CHARS];
        long remaining = value;
        for (int i = TIMESTAMP_CHARS - 1; i >= 0; i--) {
            chars[i] = BASE36[(int) (remaining % 36)];
            remaining /= 36;
        }
        return new String(chars);
    }

    private static String encodeRandomness(byte[] bytes) {
        String encoded = new BigInteger(1, bytes).toString(36);
        if (encoded.length() > RANDOMNESS_CHARS) {
            throw new IllegalStateException("96-bit randomness does not fit in 19 Base36 characters");
        }
        StringBuilder out = new StringBuilder(RANDOMNESS_CHARS);
        for (int i = encoded.length(); i < RANDOMNESS_CHARS; i++) {
            out.append('0');
        }
        return out.append(encoded).toString();
    }

    private static long decodeTimestamp(String value, int offset, int length) {
        long decoded = 0L;
        for (int i = 0; i < length; i++) {
            int digit = DIGIT_VALUES[value.charAt(offset + i)];
            if (digit < 0) {
                throw new PikaidFormatException("invalid Base36 digit at index " + (offset + i));
            }
            decoded = decoded * 36 + digit;
        }
        if (decoded > MAX_TIMESTAMP) {
            throw new PikaidFormatException("timestamp exceeds maximum " + MAX_TIMESTAMP + ": " + decoded);
        }
        return decoded;
    }

    private static BigInteger decodeBase36(String value, int offset, int length) {
        BigInteger decoded = BigInteger.ZERO;
        for (int i = 0; i < length; i++) {
            int digit = DIGIT_VALUES[value.charAt(offset + i)];
            if (digit < 0) {
                throw new PikaidFormatException("invalid Base36 digit at index " + (offset + i));
            }
            decoded = decoded.multiply(BASE).add(BigInteger.valueOf(digit));
        }
        return decoded;
    }

    private static byte[] toFixedWidthBytes(BigInteger value, int length) {
        if (value.signum() < 0 || value.bitLength() > length * 8) {
            throw new PikaidFormatException("value does not fit in " + length + " bytes");
        }
        byte[] out = new byte[length];
        BigInteger remaining = value;
        for (int i = length - 1; i >= 0; i--) {
            out[i] = remaining.byteValue();
            remaining = remaining.shiftRight(8);
        }
        return out;
    }

    private static final class DefaultGeneratorHolder {
        private static final PikaidGenerator INSTANCE = new PikaidGenerator();
    }
}
