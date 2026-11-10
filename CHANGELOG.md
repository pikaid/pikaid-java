# Changelog

All notable changes to the official Java implementation of pikaid are documented
here. The identifier format itself is immutable and unversioned; this changelog
tracks the library only.

## [1.0.0] — 2026-11-07

Prepared stable release. Not yet published to Maven Central; see the
"Release status" section of the README for the outstanding prerequisites.

### Added

- Official Java implementation of the pikaid v1 format: 26 lowercase Base36
  characters (7-character unsigned UTC timestamp plus 19 characters encoding
  96 bits of entropy) and the equivalent 17-byte binary representation.
- Strict parsing and binary decoding that reject malformed input, overflow, and
  non-canonical values with `PikaidFormatException` instead of coercing them.
- `PikaidGenerator` with injectable `SecureRandom` and `Clock`; generation fails
  closed when the entropy source fails and validates the timestamp before
  consuming randomness.
- Reproducible library, sources, and Javadoc jars with pinned
  `project.build.outputTimestamp` and stable manifest metadata.
- The MIT license text is packaged as `META-INF/LICENSE` in the library and
  sources jars, so bare-jar redistribution carries the notice.
- `release` Maven profile with GPG signing and the Central Publishing plugin.
- A test-only JSON-Lines conformance adapter
  (`dev.pikaid.conformance.ConformanceAdapter`) that exposes the public API to
  the official `pikaid-specs` conformance runner. It is compiled with the test
  sources and excluded from every published jar.

### Changed

- CI now runs the official `pikaid/pikaid-specs` conformance package at the
  pinned commit `059780cc0e5b113443812720d95c630649bf3aa3` (package revision
  `1.0.0`, spec revision `1.0.4`) in the `official-conformance` job. The job
  verifies the checked-out commit and runs the package's own checksum, schema,
  and oracle checks before executing the suite with `--automated-only`.

- Project version moved from `0.1.0-SNAPSHOT` to the release version `1.0.0`.
- POM metadata expanded with SCM, license, developer, and inception-year fields
  required for Maven Central.
- CI now verifies that `.ai/` is untracked and absent from every generated jar
  and from Git source archives.
- Every GitHub Action referenced by the CI workflow is pinned to an audited
  exact commit SHA (with the released tag in a comment) instead of a mutable
  major-version tag; CI also asserts the packaged `META-INF/LICENSE` notice in
  the library and sources jars.

### Validation

- 112 JUnit 5 tests covering the official PHP v1.2.0 interoperability fixture,
  an independent oracle, boundary and overflow cases, randomized round trips,
  ordering, and CSPRNG failure behavior.
- JaCoCo coverage gates, `-Xlint:all -Werror`, and Javadoc generation run as part
  of `./mvnw verify` on JDK 17 and JDK 21.
