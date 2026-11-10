package dev.pikaid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class FormatValidationTest {

    @ParameterizedTest(name = "rejects [{0}]")
    @NullSource
    @ValueSource(strings = {
            "",
            "0",
            "0000000000000000000000000",
            "000000000000000000000000000",
            "000000A0000000000000000000",
            "000000-0000000000000000000",
            "000000_0000000000000000000",
            "000000 0000000000000000000",
            "0000000000000000000000000\n",
            "0000000\u00e9000000000000000000000",
            "0000000\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410\u0410",
            "0000000Z0000000000000000000",
            "0000000+0000000000000000000",
            "0000000/0000000000000000000",
            "0000000:0000000000000000000",
            "0000000@0000000000000000000",
            "0000000`0000000000000000000"
    })
    void invalidTextIsRejectedWithoutCoercion(String value) {
        assertFalse(Pikaid.isValid(value));
        assertThrows(PikaidFormatException.class, () -> Pikaid.parse(value));
    }

    @ParameterizedTest(name = "accepts [{0}]")
    @ValueSource(strings = {
            "00000000000000000000000000",
            "00000010000000000000000001",
            "0s44we8002loz3po6mcf6vsn23",
            "zzzzzzz0000000000000000000",
            "zzzzzzz7oiylpimjg5u2ca1ypr"
    })
    void boundaryTextIsAccepted(String value) {
        assertTrue(Pikaid.isWellFormed(value));
        assertTrue(Pikaid.isValid(value));
        assertEquals(value, Pikaid.parse(value).toString());
    }

    @Test
    void shapeCheckIsNotFullValidation() {
        String entropyOverflow = "0000000zzzzzzzzzzzzzzzzzzz";
        assertTrue(Pikaid.isWellFormed(entropyOverflow));
        assertFalse(Pikaid.isValid(entropyOverflow));
    }

    @Test
    void nullAndWrongLengthsAreNotWellFormed() {
        assertFalse(Pikaid.isWellFormed(null));
        assertFalse(Pikaid.isWellFormed(""));
        assertFalse(Pikaid.isWellFormed("0000000000000000000000000"));
        assertFalse(Pikaid.isWellFormed("000000000000000000000000000"));
    }

    @Test
    void nonAsciiLengthInCodeUnitsCannotPass() {
        // 26 UTF-16 code units, but the first character is outside ASCII.
        String value = "0000000" + "\u00e9" + "0".repeat(18);
        assertEquals(26, value.length());
        assertFalse(Pikaid.isWellFormed(value));
        assertThrows(PikaidFormatException.class, () -> Pikaid.parse(value));
    }

    @ParameterizedTest(name = "rejects [{0}]")
    @ValueSource(strings = {
            "A0000000000000000000000000",
            "000000-0000000000000000000",
            "0000000000000000000000000 ",
            "0000000000000000000000000\u0000",
            "0000000000000000000000000\u200b",
            "000000000000000000000000\r\n",
            "0000000000000000000000000\u00e9"
    })
    void shapeCheckRejectsInvalidAsciiAlphabetCharacters(String value) {
        assertEquals(26, value.length());
        assertFalse(Pikaid.isWellFormed(value));
        assertFalse(Pikaid.isValid(value));
        assertThrows(PikaidFormatException.class, () -> Pikaid.parse(value));
    }

    @Test
    void maximumTimestampAndRandomnessAreValid() {
        Pikaid max = Pikaid.parse("zzzzzzz7oiylpimjg5u2ca1ypr");
        assertEquals(Pikaid.MAX_TIMESTAMP, max.timestamp());
        assertEquals("ffffffffffffffffffffffff", max.randomnessHex());
    }

    @Test
    void entropyJustAboveNinetySixBitsIsRejected() {
        String value = "0000000" + "7oiylpimjg5u2ca1yps";
        assertEquals(26, value.length());
        assertTrue(Pikaid.isWellFormed(value));
        assertFalse(Pikaid.isValid(value));
        assertThrows(PikaidFormatException.class, () -> Pikaid.parse(value));
    }

    @Test
    void parseErrorMessagesNameTheProblem() {
        PikaidFormatException length = assertThrows(PikaidFormatException.class, () -> Pikaid.parse("abc"));
        assertTrue(length.getMessage().contains("exactly 26"));

        PikaidFormatException character =
                assertThrows(PikaidFormatException.class, () -> Pikaid.parse("A0000000000000000000000000"));
        assertTrue(character.getMessage().contains("index 0"));

        PikaidFormatException overflow = assertThrows(PikaidFormatException.class,
                () -> Pikaid.parse("0000000zzzzzzzzzzzzzzzzzzz"));
        assertTrue(overflow.getMessage().contains("96 bits"));
    }
}
