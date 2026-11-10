package dev.pikaid;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;

/**
 * A pikaid generator configured with a cryptographic random source and a clock.
 *
 * <p>The generator obtains exactly twelve bytes per identifier from the supplied
 * {@link SecureRandom}. Random-source failures propagate; generation fails
 * closed and never substitutes a weaker source. A custom {@code SecureRandom}
 * or {@link Clock} is useful for tests, but production code should normally use
 * the no-argument constructor.</p>
 *
 * <p>This class is thread-safe when its {@link SecureRandom} is thread-safe,
 * which is true for the platform default and standard JDK implementations.</p>
 */
public final class PikaidGenerator {

    private final SecureRandom secureRandom;
    private final Clock clock;

    /**
     * Creates a generator with the platform default {@link SecureRandom} and the
     * system UTC clock.
     */
    public PikaidGenerator() {
        this(new SecureRandom(), Clock.systemUTC());
    }

    /**
     * Creates a generator with a supplied random source and the system UTC clock.
     *
     * @param secureRandom cryptographic random source, not {@code null}
     */
    public PikaidGenerator(SecureRandom secureRandom) {
        this(secureRandom, Clock.systemUTC());
    }

    /**
     * Creates a generator with a supplied random source and clock.
     *
     * @param secureRandom cryptographic random source, not {@code null}
     * @param clock        clock used by {@link #generate()}, not {@code null}
     */
    public PikaidGenerator(SecureRandom secureRandom, Clock clock) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Generates a pikaid using the configured clock and a fresh twelve-byte
     * cryptographic random suffix.
     *
     * @return a new valid pikaid
     * @throws PikaidFormatException if the clock is outside the representable
     *                               timestamp range
     * @throws RuntimeException      if the random source fails
     */
    public Pikaid generate() {
        return generate(clock.instant().getEpochSecond());
    }

    /**
     * Generates a pikaid with an explicitly supplied timestamp.
     *
     * @param timestamp unsigned Unix seconds in {@code 0..78,364,164,095}
     * @return a new valid pikaid
     * @throws PikaidFormatException if the timestamp is out of range
     * @throws RuntimeException      if the random source fails
     */
    public Pikaid generate(long timestamp) {
        if (timestamp < Pikaid.MIN_TIMESTAMP || timestamp > Pikaid.MAX_TIMESTAMP) {
            throw new PikaidFormatException(
                    "timestamp out of range: " + timestamp
                            + " (expected " + Pikaid.MIN_TIMESTAMP + ".." + Pikaid.MAX_TIMESTAMP + ")");
        }
        byte[] randomness = new byte[Pikaid.RANDOMNESS_BYTES];
        secureRandom.nextBytes(randomness);
        return Pikaid.of(timestamp, randomness);
    }

    /**
     * Generates a pikaid and returns its canonical 17-byte binary form.
     *
     * @return a new 17-byte binary pikaid
     * @throws PikaidFormatException if the clock is outside the representable
     *                               timestamp range
     * @throws RuntimeException      if the random source fails
     */
    public byte[] generateBinary() {
        return generate().toBinary();
    }

    /**
     * Generates a pikaid with an explicitly supplied timestamp and returns its
     * canonical 17-byte binary form.
     *
     * @param timestamp unsigned Unix seconds in {@code 0..78,364,164,095}
     * @return a new 17-byte binary pikaid
     * @throws PikaidFormatException if the timestamp is out of range
     * @throws RuntimeException      if the random source fails
     */
    public byte[] generateBinary(long timestamp) {
        return generate(timestamp).toBinary();
    }
}
