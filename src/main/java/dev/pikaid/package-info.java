/**
 * Official Java implementation of pikaid.
 *
 * <p>A pikaid is an immutable 26-character identifier: a seven-character
 * lowercase Base36 Unix timestamp in whole seconds, followed by nineteen
 * lowercase Base36 characters encoding 96 bits of cryptographic randomness.
 * Its binary form is exactly 17 bytes: a five-byte big-endian timestamp and
 * twelve unchanged randomness bytes.</p>
 *
 * <p>Use {@link dev.pikaid.Pikaid#generate()} for normal generation and
 * {@link dev.pikaid.Pikaid#parse(String)} or
 * {@link dev.pikaid.Pikaid#fromBinary(byte[])} for strict decoding. Invalid
 * input raises {@link dev.pikaid.PikaidFormatException}; it is never silently
 * repaired. The text and binary representations have matching ordering for
 * identifiers with different timestamps, but identifiers created within the
 * same second are ordered by their random suffix, not by generation order.</p>
 *
 * <p>pikaid values are not secrets, authentication tokens, or a substitute for
 * authorization checks, and randomness reduces but does not eliminate the
 * possibility of collisions.</p>
 *
 * @see <a href="https://github.com/pikaid/pikaid-specs/tree/v1.0.4">pikaid specification 1.0.4</a>
 */
package dev.pikaid;
