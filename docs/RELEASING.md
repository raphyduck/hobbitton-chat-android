# Releasing

How releases are versioned, signed, and published.

## Versioning

The app version is calendar-based — **`YYYY.MM.PATCH`** (zero-padded month, e.g.
`2026.06.0`) — and lives in **one place**: `version.properties` at the repo root.

```properties
versionName=2026.06.0      # calver YYYY.MM.PATCH — bumped by the release workflow
```

- Year and month come from the current **UTC** date automatically when bumping; the only
  thing a release decides is patch-level vs release-candidate. PATCH resets to 0 when the
  month rolls over and increments for further releases within the same month.
- `AndroidApplicationConventionPlugin` reads `versionName` and **derives** `versionCode` as
  `YEAR*10000 + MONTH*100 + PATCH`: `2026.06.0` → `20260600`, `2026.06.1` → `20260601`.
  Monotonic as the date advances; limits are MONTH ≤ 99 (trivially true), PATCH ≤ 99.
- The diagnostic log's startup header reads the *installed* version via `AppInfo` (package
  metadata), so it can never drift.

Bump it with the script (also run by the release workflow). For example, running in
June 2026 with stored version `2026.05.2`:

```bash
scripts/bump-version.sh patch   # 2026.05.2 -> 2026.06.0  (new month: PATCH resets)
scripts/bump-version.sh patch   # 2026.06.0 -> 2026.06.1  (same month: PATCH+1)
```

### Release candidates

To ship a candidate before a stable release, use the pre-release bumps:

```bash
scripts/bump-version.sh prepatch  # 2026.06.1     -> 2026.06.2-rc1  (start a candidate)
scripts/bump-version.sh rc        # 2026.06.2-rc1 -> 2026.06.2-rc2  (next candidate)
scripts/bump-version.sh finalize  # 2026.06.2-rc2 -> 2026.06.2      (promote to stable)
```

`prepatch` starts a candidate for the next patch version; `rc` advances the candidate
number; `finalize` drops the suffix to promote the current candidate. `rc` and `finalize`
never touch the version core: a candidate started in June and finalized in July still
ships as `2026.06.x` — the date reflects when the release train started. A hyphenated
version is published as a GitHub **pre-release**, which Obtainium skips unless the user
enables *Include prereleases*.

> The `-rcN` suffix is stripped when deriving the versionCode, so `2026.06.2-rc1`,
> `2026.06.2-rc2`, and the final `2026.06.2` all share one versionCode. Candidates install
> over each other fine, but if users update *from* a candidate *to* the final build, bump
> the patch instead of finalizing so the version visibly advances.

### Calver cutover

Versions before the calver switch were semver (`0.1.0` – `0.1.3`) under the same
versionCode packing (`0.1.3` → `103`), so codes stayed monotonic across the cutover
(`103` → first calver release's `YYYYMM00`). The jump is intentionally one-way: calver
codes are ~20M, so there is no path back to small semver codes without an epoch scheme.

## One-time signing key setup

Releases must be signed with **one permanent key**. If the key ever changes, every existing
user's update fails with a signature conflict and recovery requires uninstall + reinstall
(full data loss). There is no key reset for direct/sideloaded distribution.

> ### hobbitton fork — the key already exists. Do not generate a new one.
>
> | | |
> |---|---|
> | keystore | `hobbitton-release.jks` — PKCS12, RSA 4096, SHA384withRSA |
> | alias | `hobbitton` |
> | validity | 21 Aug 2026 → 6 Jan 2054 |
> | SHA-256 | `0A:6D:AF:66:F7:D1:3D:43:21:A5:F0:0D:9E:F9:1B:9F:E5:93:C8:1B:F6:2C:F0:50:3D:90:E1:35:F7:DB:19:30` |
> | where it lives | Bitwarden, *Clé de signature Android — at.hobbitton.chat (IRRÉCUPÉRABLE)* — file attached, passwords in the note |
>
> It signs `applicationId at.hobbitton.chat`. The steps below describe how it was created and how
> to wire it into CI; follow step 2 onwards. **Step 1 is history, not an instruction** — running it
> again produces a *different* key, and a different key is a different app to Android: every phone
> that already has the build would refuse the update.
>
> Verify a downloaded APK really came from this key:
>
> ```bash
> apksigner verify --print-certs app-release.apk   # compare with the SHA-256 above
> ```

1. **Generate the keystore** (do this once, keep it forever):

   ```bash
   keytool -genkeypair -v \
     -keystore librechat-release.jks \
     -alias librechat \
     -keyalg RSA -keysize 4096 -validity 10000 \
     -storetype PKCS12 \
     -dname "CN=LibreChat Mobile, O=LibreChat, C=US"
   ```

   > The `librechat` filename, alias, and dname above are historical — they record how the
   > existing release key was actually generated, before the app was renamed to Switchboard.
   > Do **not** "fix" them to match the new name: the dname is baked into the certificate, and
   > re-keying would make Android refuse the update for every existing install.

   Back it up in **2+ secure locations** (password manager / offline). Do **not** commit it
   (`.gitignore` already excludes `*.jks` and `keystore.properties`).

2. **Add GitHub repository secrets** (Settings → Secrets and variables → Actions). Ideally put
   them in an Environment named `release` with required reviewers, so the release job is gated.

   | Secret | Value |
   |---|---|
   | `SIGNING_KEYSTORE_BASE64` | `openssl base64 -A < librechat-release.jks` |
   | `SIGNING_STORE_PASSWORD` | keystore password |
   | `SIGNING_KEY_PASSWORD` | key password |
   | `SIGNING_KEY_ALIAS` | `librechat` |

3. **Publish the certificate fingerprint** in the README so users can verify:

   ```bash
   keytool -list -v -keystore librechat-release.jks -alias librechat   # SHA-256 line
   ```

### Local signed builds (optional)

Create `keystore.properties` at the repo root (git-ignored):

```properties
storeFile=librechat-release.jks
storePassword=...
keyAlias=librechat
keyPassword=...
```

Then `./gradlew :app:assembleRelease` produces a signed APK. Without env vars or this file,
release builds fall back to the debug key so local builds and CI checks still work.

## Cutting a release

1. Actions → **Release** → *Run workflow* → choose the bump (`patch` for a stable
   release, or `prepatch`/`rc`/`finalize` for the candidate flow). Year/month are
   derived from the current UTC date automatically.
2. The job bumps `version.properties`, builds a signed universal APK, signs a SLSA
   build-provenance attestation for it, and **only then** commits + tags `vYYYY.MM.P` and creates
   a **draft** GitHub Release with auto-generated notes and a `.sha256` checksum. Candidate
   versions are flagged as pre-releases automatically. If the build fails, nothing is committed
   or tagged — just re-run after fixing it.
3. Review the draft, edit the notes for users, then **publish**.
4. Obtainium / IzzyOnDroid pick up the published release automatically.

> **Drafts are invisible to Obtainium.** A release stays hidden from every user until you
> click **Publish** — Obtainium's GitHub source skips drafts. The workflow ends with a
> warning annotation reminding you to publish; don't skip it.

> **Branch protection:** the workflow pushes the version-bump commit and tag to the default
> branch, which is ruleset-protected. The `github-actions` bot can't be a ruleset bypass
> actor on a personal (non-org) repo, so the workflow authenticates the push with a
> fine-grained PAT instead. Create one (**Contents: read & write**, this repo only, short
> expiry) and add it as a secret named `RELEASE_PAT` in the `release` environment. It pushes
> as the repo owner (already a bypass actor); without it the run fails fast at the
> *Verify signing secrets* step.

## Verifying a release

Three checks are available to anyone who downloads an APK, in descending order of strength:

1. **Build provenance (strongest)** — `gh attestation verify <apk> --repo garfiec/Librechat-Mobile
   --signer-workflow garfiec/Librechat-Mobile/.github/workflows/release.yml`. This is the only
   check that resists a *tampered upload*: it's signed by GitHub's CI via Sigstore and can't be
   forged outside the runner. Each release's notes link the run + attestation, but a link is not
   proof — only this command verifies the downloaded bytes.
2. **Signing certificate** — `apksigner verify --print-certs <apk>`, compared against the
   published cert SHA-256. Catches a build signed with a different key.
3. **Checksum** — `sha256sum -c <apk>.sha256`. Catches accidental corruption or a download MITM
   only; it does *not* defend against a malicious release (the attacker would control both files).

## Distribution channels

- **Obtainium** — tracks GitHub Releases directly (see the README install section). Stable
  releases are full releases; `-rcN` candidate tags are marked pre-release.
- **IzzyOnDroid** (future) — ingests the developer-signed APK and pins the signing key via
  `AllowedAPKSigningKeys`. Use the *same* key. Reproducible builds earn a verification badge.
