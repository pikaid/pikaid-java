<p align="center">
  <a href="https://github.com/pikaid/pikaid-specs">
    <img src="https://raw.githubusercontent.com/pikaid/pikaid-specs/v1.0.4/logo.png" alt="pikaid" width="300">
  </a>
</p>

<p align="center">
  <strong>Small · Sortable · Secure ID generator</strong>
</p>

<div align="center">

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/language-Java-ED8B00.svg)](https://github.com/pikaid/pikaid-java)
[![CI](https://github.com/pikaid/pikaid-java/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/pikaid/pikaid-java/actions/workflows/ci.yml)
[![Official implementation](https://img.shields.io/badge/official-implementation-2e7d32.svg)](https://github.com/pikaid/pikaid-specs)
[![Specification 1.0.4](https://img.shields.io/badge/spec-1.0.4-4b56a6.svg)](https://github.com/pikaid/pikaid-specs/tree/v1.0.4)

</div>

**pikaid** is the **official Java implementation** of a compact, chronologically sortable, collision-resistant identifier format for databases, APIs, and distributed systems.

This is the immutable, unversioned [pikaid format](https://github.com/pikaid/pikaid-specs/tree/v1.0.4), documented in specification revision **1.0.4**.

## ✨ Why pikaid?

- **Short:** Compact, readable IDs for URLs, logs, and API resources.
- **Sortable:** Lexicographically ordered by timestamp, with **one-second precision**.
- **Collision-resistant:** 96 bits of cryptographic randomness make collisions extremely unlikely, never impossible.
- **Storage-friendly:** Use `BINARY(17)` or readable `CHAR(26)`.
- **Distributed-friendly:** No central sequence generator or coordination required.

## 🧬 Identifier format

A pikaid contains **26 lowercase Base36 characters**: a **7-character Unix-seconds timestamp** followed by **19 characters encoding 96 bits of cryptographic randomness**. Its binary form is **17 bytes**: 5 unsigned big-endian timestamp bytes followed by 12 randomness bytes.

![Identifier structure](https://raw.githubusercontent.com/pikaid/pikaid-specs/v1.0.4/structure.svg)

> **Ordering:** Different seconds sort chronologically. Within the same second, randomness—not issuance order—decides the order.

> **Security:** These are identifiers, **not secrets**, session credentials, authentication tokens, or API keys.

## ⚙️ Requirements

- Java 17 or newer. The library is compiled with `--release 17` and has no
  runtime dependencies outside the JDK.
- Maven 3.9 or newer when building from source. The included Maven Wrapper
  (`./mvnw`) downloads a compatible Maven version.

## 📦 Installation

From this public source repository, compile the library before using it locally:

```sh
git clone https://github.com/pikaid/pikaid-java.git
cd pikaid-java
./mvnw -q -DskipTests package
```

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

To install the built artifact into your local Maven repository:

```bash
./mvnw install
```

## 🚀 Basic Usage

Save this complete example as `Main.java` in the repository root and run `javac -cp target/classes Main.java && java -cp target/classes:. Main`:

```java
import dev.pikaid.Pikaid;

public class Main {
    public static void main(String[] args) {
        Pikaid id = Pikaid.generate();
        
        String text = id.toString();            // 26 lowercase Base36 characters
        byte[] binary = id.toBinary();          // exactly 17 bytes
        long timestamp = id.timestamp();        // unsigned Unix seconds
        String randomness = id.randomnessHex(); // 24 lowercase hexadecimal characters
        
        Pikaid fromText = Pikaid.parse(text);
        Pikaid fromBinary = Pikaid.fromBinary(binary);
        
        assert fromText.equals(id);
        assert fromBinary.equals(id);
        System.out.println(text);
    }
}
```

## 💡 Example Use Case

Use pikaid to identify **users, orders, invoices, uploads, events, and API resources**. Independent services can generate compact IDs without a shared counter, while timestamp-based ordering helps with indexing, sorting, and logs.

## 📚 Canonical specification

- [Canonical specification](https://github.com/pikaid/pikaid-specs)
- [Revision 1.0.4](https://github.com/pikaid/pikaid-specs/tree/v1.0.4)

Specification revisions describe the format; they do not change the encoded identifiers.

## 🧩 API Reference

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

## 🔄 Order Guarantee

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

## 🛢 Storage Recommendations

| Representation | Size | Recommended for |
| --- | ---: | --- |
| **BINARY(17)** | 17 bytes | Compact primary keys and indexes |
| **CHAR(26)** | 26 characters | Readable SQL, debugging, URLs, and logs |

Use an **ASCII bytewise/binary collation** for text columns (for example, `ascii_bin` on MariaDB/MySQL). Case-insensitive or locale-aware sorting is not a substitute for canonical bytewise comparison.

## ✅ Verification and release status

The [CI workflow](https://github.com/pikaid/pikaid-java/actions/workflows/ci.yml) runs native tests, pinned automated conformance checks, and language-specific quality gates. To run the native checks locally from a checkout: `./mvnw verify`.

**Automated passing tests are not a security certification.** The independent CSPRNG source and failure-path audits (`P-GENERATE-RNG-SOURCE` and `P-GENERATE-RNG-FAILURE`) still require separate human evidence.

<details>
<summary>Platform, tooling, and conformance details</summary>

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

</details>

Registry publication is not implied by a green CI: use the installation method documented above.

## 🤝 Contributing

Contributions are always welcome! 💙

Whether you're fixing a typo, improving documentation, adding tests, or making pikaid even better, every contribution is appreciated.

**Want to help? Here's how:**

- 🐛 **Found a bug?** [Open an issue](https://github.com/pikaid/pikaid-java/issues) with clear reproduction steps.
- 💡 **Have an idea?** Start with an issue before making major changes.
- 🛠️ **Ready to contribute?** Open a pull request explaining your changes.
- 🧪 **Keep it tested.** Add meaningful regression tests for behavior changes and follow the existing conventions.
- ✅ **Make sure CI passes.** All required checks must be green before a pull request is merged. No shortcuts!

**One important rule:** pikaid's identifier format is immutable. Contributions must preserve the [canonical specification (revision 1.0.4)](https://github.com/pikaid/pikaid-specs/tree/v1.0.4).

🔐 **Found a security vulnerability?** Report it privately using GitHub's security reporting (when available), or [contact the maintainer](mailto:larananas.dev@proton.me)—not through a public issue.

**Every improvement matters, big or small. Thanks for helping make pikaid better!** 🐇

## 📄 License

The implementation is available under the [MIT License](LICENSE).

## ✉️ Contact

For licensing or project-related questions: [larananas.dev@proton.me](mailto:larananas.dev@proton.me).

