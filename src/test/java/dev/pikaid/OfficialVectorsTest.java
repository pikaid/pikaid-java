package dev.pikaid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * Runs the complete official PHP v1.2.0 interoperability fixture.
 *
 * <p>Source: {@code pikaid/pikaid-php} tag {@code v1.2.0}, commit
 * {@code e3f843edeb5157a5d2d25196cac0d69580810a95},
 * {@code tests/vectors/pikaid-v1.json}. The fixture filename is historical; the
 * identifier format is immutable and unversioned.</p>
 */
class OfficialVectorsTest {

    /**
     * SHA-256 of the vendored fixture, pinned so an accidental or malicious edit
     * cannot silently change the interoperability contract. The value is the
     * digest of {@code pikaid/pikaid-php} commit
     * {@code e3f843edeb5157a5d2d25196cac0d69580810a95}.
     */
    private static final String FIXTURE_SHA256 =
            "b0c9dfdd063613424414fef5c9065fbd2f81d645d0ab42b34e27cde715613500";

    private static final HexFormat HEX = HexFormat.of();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fixtureIsCompleteAndPinned() {
        JsonNode root = vectors();
        JsonNode meta = root.get("meta");

        assertEquals(FIXTURE_SHA256, sha256(fixtureBytes()), "vendored fixture digest");

        assertEquals("pikaid", meta.get("project").asText());
        assertEquals(26, meta.get("string_length").asInt());
        assertEquals("base36-lower", meta.get("timestamp").get("encoding").asText());
        assertEquals(7, meta.get("timestamp").get("chars").asInt());
        assertEquals(0, meta.get("timestamp").get("min").asLong());
        assertEquals(Pikaid.MAX_TIMESTAMP, meta.get("timestamp").get("max").asLong());
        assertEquals(12, meta.get("entropy").get("bytes").asInt());
        assertEquals("base36-lower", meta.get("entropy").get("encoding").asText());
        assertEquals(19, meta.get("entropy").get("chars").asInt());
        assertEquals(17, meta.get("binary").get("bytes").asInt());
        assertEquals("[5 bytes timestamp big-endian][12 bytes entropy]",
                meta.get("binary").get("layout").asText());
        assertEquals("1.0.4", meta.get("spec_revision").asText());
        assertEquals("immutable and unversioned", meta.get("identifier_format").asText());

        assertEquals(4, root.get("valid").size(), "valid vector count");
        assertEquals(6, root.get("invalid").get("string").size(), "invalid string vector count");
        assertEquals(4, root.get("invalid").get("binary").size(), "invalid binary vector count");
    }

    @Test
    void everyValidVectorDecodesAndReencodes() {
        int checked = 0;
        for (JsonNode vector : vectors().get("valid")) {
            String name = vector.get("name").asText();
            long timestamp = vector.get("timestamp").asLong();
            String entropyHex = vector.get("entropy_hex").asText();
            String id = vector.get("id").asText();
            String binaryHex = vector.get("bin_hex").asText();

            Pikaid parsed = Pikaid.parse(id);
            assertEquals(timestamp, parsed.timestamp(), name + " timestamp");
            assertEquals(entropyHex, parsed.randomnessHex(), name + " randomness");
            assertEquals(id, parsed.toString(), name + " canonical text");
            assertEquals(binaryHex, HEX.formatHex(parsed.toBinary()), name + " binary");

            Pikaid assembled = Pikaid.of(timestamp, HEX.parseHex(entropyHex));
            assertEquals(parsed, assembled, name + " assembled equality");
            assertEquals(id, assembled.toString(), name + " assembled text");

            Pikaid decoded = Pikaid.fromBinary(HEX.parseHex(binaryHex));
            assertEquals(parsed, decoded, name + " binary equality");
            assertEquals(id, decoded.toString(), name + " binary-to-text");

            assertTrue(Pikaid.isValid(id), name + " isValid");
            assertTrue(Pikaid.isWellFormed(id), name + " isWellFormed");
            checked++;
        }
        assertEquals(4, checked, "all official valid vectors executed");
    }

    @Test
    void everyInvalidStringVectorIsRejected() {
        int checked = 0;
        for (JsonNode vector : vectors().get("invalid").get("string")) {
            String name = vector.get("name").asText();
            String id = vector.get("id").asText();

            assertFalse(Pikaid.isValid(id), name + " isValid");
            assertThrows(PikaidFormatException.class, () -> Pikaid.parse(id), name + " parse");
            checked++;
        }
        assertEquals(6, checked, "all official invalid string vectors executed");
    }

    @Test
    void everyInvalidBinaryVectorIsRejected() {
        int checked = 0;
        for (JsonNode vector : vectors().get("invalid").get("binary")) {
            String name = vector.get("name").asText();
            byte[] binary = HEX.parseHex(vector.get("bin_hex").asText());

            assertThrows(PikaidFormatException.class, () -> Pikaid.fromBinary(binary), name);
            checked++;
        }
        assertEquals(4, checked, "all official invalid binary vectors executed");
    }

    @Test
    void entropyOverflowIsWellFormedButNotValid() {
        String overflow = "0000000zzzzzzzzzzzzzzzzzzz";
        assertTrue(Pikaid.isWellFormed(overflow));
        assertFalse(Pikaid.isValid(overflow));
        assertThrows(PikaidFormatException.class, () -> Pikaid.parse(overflow));
    }

    private static JsonNode vectors() {
        try {
            return MAPPER.readTree(fixtureBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot parse official pikaid vectors", e);
        }
    }

    private static byte[] fixtureBytes() {
        try (InputStream in = OfficialVectorsTest.class.getResourceAsStream("/vectors/pikaid-v1.json")) {
            assertNotNull(in, "official vector resource must be present");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read official pikaid vectors", e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every supported JDK", e);
        }
    }
}
