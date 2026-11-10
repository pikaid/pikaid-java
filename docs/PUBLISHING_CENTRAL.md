# Maven Central release setup (Java)

This project publishes `dev.pikaid:pikaid` via `.github/workflows/publish-central.yml`.
Once configured, each matching GitHub tag triggers Maven verification and
automatic publishing through Central Portal. No manual staging approval is needed.

## One-time setup

1. Register at https://central.sonatype.com/ and **verify ownership of the
   `dev.pikaid` namespace**. The current group ID will be rejected without
   namespace ownership. If this namespace cannot be verified, change to a
   verified namespace and update Java consumer coordinates before the first release.
2. Create a Central Portal user token. Save its **generated username** and
   **generated password** as GitHub Actions secrets `CENTRAL_USERNAME`
   and `CENTRAL_PASSWORD`.
3. Generate a GPG signing key and publish its public key to a public keyserver.
   Store the ASCII-armored private key as `GPG_PRIVATE_KEY` and its
   passphrase as `GPG_PASSPHRASE` in GitHub Actions secrets.
4. Create the GitHub environment named `maven-central` (Settings ->
   Environments). Configure protection as appropriate. No extra human
   approval is required for routine releases.
5. Protect release tags and access to the release secrets.

## Every release

1. Update `<version>` in `pom.xml` to a new stable version.
2. Ensure the normal CI checks pass and create a GitHub tag `vX.Y.Z`
   against the same commit.
3. The workflow verifies that the Maven version equals the tag, runs
   `./mvnw verify`, signs the main, sources, Javadoc and POM artifacts,
   and invokes the Central Publishing Maven plugin with automatic release
   enabled. It waits for the deployment to become published.

Maven Central versions are immutable. If a deployment fails, fix the
underlying problem rather than skipping validation or disabling signing.
See https://central.sonatype.org/publish/publish-portal-maven/.

**No per-version upload or manual approval is needed after initial setup.**
