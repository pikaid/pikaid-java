<p align="center">
  <img src="https://raw.githubusercontent.com/pikaid/pikaid-specs/v1.0.4/logo.png" alt="pikaid" width="300" />
</p>

<p align="center">
  <strong>Small · Sortable · Secure ID generator</strong>
</p>

# pikaid — Official Java Implementation

Official Java implementation of [pikaid](https://github.com/pikaid), a compact,
chronologically sortable identifier for databases, APIs, and distributed
systems.

A pikaid is exactly **26 lowercase ASCII Base36 characters**:

```text
0s44we8 002loz3po6mcf6vsn23
└──┬───┘ └────────┬────────┘
   │              └── 19 characters, 96 bits of cryptographic randomness
   └── 7 characters, unsigned Unix timestamp in whole seconds
```

The equivalent binary representation is exactly **17 bytes**: a five-byte
unsigned big-endian timestamp followed by the twelve randomness bytes unchanged.

The identifier format is **immutable and unversioned**. Specification revision
**1.0.4** is normative:
[`pikaid/pikaid-specs` at `v1.0.4`](https://github.com/pikaid/pikaid-specs/tree/v1.0.4).

**Status: v1.0.0 — release prepared, not yet published.** The
`dev.pikaid:pikaid:1.0.0` coordinate becomes usable once Maven Central
publication completes. Until then, build and install the artifact locally from
this repository. Publication is blocked on Maven Central namespace verification
and maintainer signing credentials; see [Release status](#release-status).

## Requirements

- Java 17 or newer. The library is compiled with `--release 17` and has no
  runtime dependencies outside the JDK.
- Maven 3.9 or newer when building from source. The included Maven Wrapper
  (`./mvnw`) downloads a compatible Maven version.

## Installation

```xml
<dependency>
  <groupId>dev.pikaid</groupId>
  <artifactId>pikaid</artifactId>
  <version>1.0.0</version>
</dependency>
```

```groovy
implementation("dev.pikaid:pikaid:1.0.0")
```

Until publication completes, install the artifact into your local Maven
repository:

```bash
./mvnw install
```

## Quick start

```java
import dev.pikaid.Pikaid;

Pikaid id = Pikaid.generate();

String text = id.toString();            // 26 lowercase Base36 characters
byte[] binary = id.toBinary();          // exactly 17 bytes
long timestamp = id.timestamp();        // unsigned Unix seconds
String randomness = id.randomnessHex(); // 24 lowercase hexadecimal characters

Pikaid fromText = Pikaid.parse(text);
Pikaid fromBinary = Pikaid.fromBinary(binary);

assert fromText.equals(id);
assert fromBinary.equals(id);
```

### Explicit timestamp and deterministic construction

```java
import dev.pikaid.Pikaid;
import java.util.HexFormat;

// Randomness still comes from the CSPRNG.
Pikaid fixed = Pikaid.generate(1_700_000_000L);

// Deterministic factory for fixtures and interoperability pipelines.
// Normal production generation should use generate(...) or PikaidGenerator.
Pikaid known = Pikaid.of(
        1_700_000_000L,
        HexFormat.of().parseHex("00112233445566778899aabb"));

System.out.println(known); // 0s44we8002loz3po6mcf6vsn23
```

### Validation

```java
if (Pikaid.isValid(candidate)) {
    Pikaid id = Pikaid.parse(candidate);
    // ...
}

Pikaid.parse(candidate);        // throws PikaidFormatException if invalid
Pikaid.fromBinary(bytes);       // throws PikaidFormatException if invalid
```

`Pikaid.isWellFormed` checks only the 26-character length and lowercase Base36
alphabet. It does **not** verify the 96-bit randomness limit, so its result must
not be described as full validity. Use `Pikaid.isValid` for semantic validation.

Strict parsing never normalizes, trims, case-folds, truncates, or silently
coerces input. Uppercase letters, non-ASCII characters, separators, wrong
lengths, randomness above `2^96 - 1`, and binary timestamps above
`78,364,164,095` are all rejected.

### Configurable generator

```java
import dev.pikaid.Pikaid;
import dev.pikaid.PikaidGenerator;

PikaidGenerator generator = new PikaidGenerator(); // SecureRandom + system UTC clock
Pikaid id = generator.generate();
byte[] binary = generator.generateBinary();
```

`PikaidGenerator` also accepts a `SecureRandom` and a `Clock` for controlled
environments. Random-source failures propagate; generation fails closed and
never falls back to a weaker source.

## Public API

| Method | Purpose |
| ------ | ------- |
| `Pikaid.generate()` | Generate with the current UTC second and a CSPRNG. |
| `Pikaid.generate(long timestamp)` | Generate with an explicit timestamp and a CSPRNG. |
| `Pikaid.generateBinary()` | Generate and return the 17-byte binary form. |
| `Pikaid.of(long timestamp, byte[] randomness)` | Deterministic factory for fixtures and raw-field pipelines. |
| `Pikaid.parse(String value)` | Strictly parse 26-character text. |
| `Pikaid.fromBinary(byte[] value)` | Strictly decode exactly 17 bytes. |
| `Pikaid.isValid(String value)` | Full semantic validation, returning `false` on failure. |
| `Pikaid.isWellFormed(String value)` | Shape-only length/alphabet check. |
| `timestamp()` / `timestampInstant()` | Timestamp as unsigned seconds or a UTC `Instant`. |
| `randomness()` / `randomnessHex()` | Defensive copy of the twelve bytes or lowercase hex. |
| `toBinary()` | New 17-byte binary representation. |
| `toString()` | Canonical 26-character text. |
| `compareTo(Pikaid other)` | Timestamp, then unsigned suffix order. |

All public types and members are documented in the generated Javadoc.

## Ordering, storage, and security

- **Ordering guarantee:** for two identifiers with different timestamps, the
  earlier timestamp sorts first in both text and binary forms under
  ASCII-compatible lexicographic or unsigned bytewise comparison.
- **No same-second monotonicity:** identifiers created within the same second
  are ordered by their random suffix, not by generation order. pikaid is not a
  sequence generator and its timestamp has one-second precision.
- **No uniqueness guarantee:** 96 bits of randomness make collisions unlikely,
  but not impossible. Enforce uniqueness where required, for example with a
  database primary key or unique constraint.
- **Not a secret:** pikaid values are readable, are not authentication tokens,
  and do not replace authorization checks. No checksum is present, so a
  transcription error can still produce another valid identifier.
- **Database storage:** use `BINARY(17)` or an equivalent fixed-width binary
  type, or `CHAR(26)`/`VARCHAR(26)` with a verified ASCII-compatible or binary
  collation. Default case-insensitive or punctuation-insensitive collations are
  not automatically safe for equality or ordering.

The approximate collision probability for `n` identifiers in one timestamp
bucket is `1 - exp(-n(n-1) / (2 * 2^96))`; the 50% threshold is about
`3.31 * 10^14` identifiers in the same second.

## Building and testing

The supported build runtimes are JDK 17 and JDK 21. The project targets Java 17
bytecode and is verified on both toolchains.

```bash
export JAVA_HOME=/path/to/jdk-17   # or jdk-21
./mvnw verify
```

The `verify` lifecycle compiles with `-Xlint:all -Werror`, runs the full JUnit 5
suite, enforces JaCoCo coverage thresholds, generates Javadoc, and builds the
library, sources, and Javadoc jars. The MIT license text is packaged as
`META-INF/LICENSE` in both the library and sources jars, so redistributors that
ship a bare jar carry the notice even without the repository checkout. Generated
files are reproducible for a given JDK and Maven toolchain:
`project.build.outputTimestamp` is pinned, so repeated clean builds of the same
commit with the same toolchain produce byte-identical artifacts. Different JDKs
may emit different but compatible bytecode and Javadoc HTML, so cross-toolchain
builds are verified for behavior and content rather than byte equality.

The local JUnit 5 suite runs on every platform in the CI matrix and includes:

- every vector in the vendored official PHP `v1.2.0` fixture (4 valid, 6 invalid
  strings, 4 invalid binary values);
- independent known-answer cases derived from the specification and a separate
  Base36 implementation;
- malformed, non-ASCII, uppercase, overflow, and boundary cases;
- zero-padding and maximum timestamp/randomness boundaries;
- seeded randomized round trips across text and binary, plus generated-ID
  sanity checks;
- cryptographic-source failure tests that prove generation fails closed;
- cross-representation ordering checks;
- README examples mirrored by executable tests.

The official fixture is vendored at
`src/test/resources/vectors/pikaid-v1.json` with SHA-256
`b0c9dfdd063613424414fef5c9065fbd2f81d645d0ab42b34e27cde715613500`.
It comes from
[`pikaid/pikaid-php` `v1.2.0`](https://github.com/pikaid/pikaid-php/blob/v1.2.0/tests/vectors/pikaid-v1.json),
commit `e3f843edeb5157a5d2d25196cac0d69580810a95`; the `pikaid-v1` filename is
historical and does not version the identifier format. The digest is asserted by
`OfficialVectorsTest`, so any change to the vendored file fails the build.

### Official conformance suite

CI also runs the official
[`pikaid/pikaid-specs` conformance package](https://github.com/pikaid/pikaid-specs/tree/059780cc0e5b113443812720d95c630649bf3aa3/conformance)
in the `official-conformance` job. The package is not vendored: the job fetches
`pikaid/pikaid-specs` at exactly
`059780cc0e5b113443812720d95c630649bf3aa3`, fails if the checked-out commit
differs, and runs the package's own checksum, schema, and independent-oracle
validation before any fixture is used. Suite package revision `1.0.0`
(`status=OFFICIAL`, spec revision `1.0.4`) contains 1,100 fixture rows: 596
`valid`, 258 `invalid-string`, 33 `invalid-binary`, 186 `ordering`, 12
`generation`, and 15 `properties`.

The Java side is `src/test/java/dev/pikaid/conformance/ConformanceAdapter.java`,
a test-only JSON-Lines bridge that calls the public `dev.pikaid` API. It is
compiled with the test sources and is not part of the published jar, sources
jar, or Javadoc jar. The workflow references every GitHub Action by an exact
commit SHA (with the released tag recorded in a comment) rather than a mutable
major-version tag. The job invokes the suite with `--automated-only`, so a
green run means every automated fixture row passed; it is not a security
certification, and the suite's two manual randomness-source audits
(`P-GENERATE-RNG-SOURCE` and `P-GENERATE-RNG-FAILURE`) remain outstanding and
are reported as such rather than claimed as passed.

## Release status

Version `1.0.0` is prepared but **not published**. The source tree, POM
metadata, reproducible packaging, and a `release` Maven profile are ready; the
following prerequisites are still missing and are therefore documented, not
assumed:

1. **Maven Central namespace ownership.** The `dev.pikaid` groupId must be
   verified with Maven Central, which requires proof of control (a DNS TXT
   record for the domain or a verified GitHub namespace) before the first
   deployment. That verification has not been completed for this repository.
2. **Signing credentials.** The `release` profile activates
   `maven-gpg-plugin` for artifact signing, but no maintainer GPG key is
   configured in this environment, so signed artifacts cannot be produced here.
3. **Repository visibility and approvals.** The GitHub repository is currently
   private. A public Central release and its SCM links assume a publicly
   readable source repository.

Until those are resolved, no tag, signature, or Central deployment is created.
Once available, the release is built with:

```bash
./mvnw -Prelease -Dgpg.keyname=<key-id> deploy
```

The `release` profile signs the artifacts and stages them through the Central
Publishing plugin with `autoPublish=false`, so publication requires a separate,
explicit approval step. `./mvnw verify` alone never publishes anything.

## Repository workspace

`.ai/` is a local-only workspace for generated reports, logs, and reference
copies. It is ignored by the root `/.ai/` rule and excluded from the library,
sources, and Javadoc jars and from source archives, so it is never committed or
published.

## License

MIT. See [LICENSE](LICENSE).
