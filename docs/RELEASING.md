# Releasing FitGPX

## One-time setup: the signing key

Android only installs an update if it's signed with the **same key** as the installed app. Losing the key
means users have to uninstall and reinstall, so **back it up** (password manager, plus an offline copy).

```sh
keytool -genkeypair -v -keystore fitgpx-release.jks -alias fitgpx \
        -keyalg RSA -keysize 4096 -validity 36500 \
        -dname "CN=FitGPX, O=FitGPX, C=CA"
base64 -w0 fitgpx-release.jks > fitgpx-release.jks.b64
```

Add these **repository secrets** (GitHub → Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `FITGPX_KEYSTORE_BASE64` | contents of `fitgpx-release.jks.b64` |
| `FITGPX_KEYSTORE_PASSWORD` | keystore password |
| `FITGPX_KEY_ALIAS` | `fitgpx` |
| `FITGPX_KEY_PASSWORD` | key password (same as keystore password if you pressed Enter) |

Then delete the `.b64` file. Never commit either file: `.gitignore` already excludes `*.jks`.

To build a signed APK locally, export the same values as environment variables
(`FITGPX_KEYSTORE=/path/to/fitgpx-release.jks`, `FITGPX_KEYSTORE_PASSWORD`, `FITGPX_KEY_ALIAS`,
`FITGPX_KEY_PASSWORD`) and run `./gradlew :app:assembleRelease`.

## Each release

1. Make sure `main` is green in CI and the manual checklist in [PLAN.md §5](PLAN.md#5-quality-testing-strategy) passes.
2. Bump `versionName` and `versionCode` in `app/build.gradle.kts`
   (`versionCode = MAJOR*10000 + MINOR*100 + PATCH`; pre-releases of the next version count up just below
   it, e.g. `1.0.0-beta.1` → `9901`). Tags containing `-` are published as GitHub pre-releases.
3. In `CHANGELOG.md`, rename *Unreleased* to `## [X.Y.Z] - YYYY-MM-DD` and start a new *Unreleased* section.
4. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (≤500 characters, used by F-Droid).
5. Commit, then tag and push:
   ```sh
   git tag -a vX.Y.Z -m "FitGPX X.Y.Z"
   git push origin main vX.Y.Z
   ```
   Or, without creating the tag yourself: `git push origin main:release`. The workflow then releases
   the `versionName` from `app/build.gradle.kts` and creates the tag.
6. The **Release** workflow builds the signed APK and CLI jar and publishes the GitHub Release with
   checksums and the signing certificate fingerprint.

## F-Droid

F-Droid does **not** have a "developer account" like the Play Store — anyone can submit an app, for free,
and there's no review fee or waiting list to join. Publishing means submitting a *recipe* (a small YAML
file describing how to build the app from source) as a merge request; F-Droid's own infrastructure then
builds, signs and hosts the APK itself. A ready-to-submit recipe lives in this repo at
[`fdroid/metadata/io.github.philtomlinson.fitgpx.yml`](../fdroid/metadata/io.github.philtomlinson.fitgpx.yml) —
it isn't read from there by F-Droid, it's just staged for the step below.

1. **Create a free GitLab.com account** (if you don't have one already) — this is separate from GitHub
   and from any Google Play developer account, and has no cost or review.
2. **Cut the release the recipe points at.** Push a `vX.Y.Z` tag (or `git push origin main:release`) so
   the version the recipe references actually exists as a GitHub Release with a signed APK.
3. **Fill in `AllowedAPKSigningKeys`** in the recipe with the SHA-256 fingerprint of our signing
   certificate, printed at the bottom of that release's notes and in `SHA256SUMS.txt`. This lets F-Droid
   ship a build signed with *our* key, so people can move between the GitHub and F-Droid builds without
   uninstalling.
4. **Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata)** on GitLab, add the filled-in recipe as
   `metadata/io.github.philtomlinson.fitgpx.yml`, and open a merge request. F-Droid pulls the app
   description, screenshots and per-version changelogs straight from this repo's
   `fastlane/metadata/android/en-US/` — nothing else to copy in.
5. A F-Droid maintainer reviews the MR (they check the build is reproducible and the source is really
   FOSS) and, once merged, builds and publishes it. This can take anywhere from days to a few weeks
   depending on their review queue — there's nothing to do but wait and answer any questions on the MR.

If you'd rather not deal with GitLab at all, F-Droid also accepts
[**Requests For Packaging**](https://gitlab.com/fdroid/rfp/-/issues) opened by anyone, including someone
other than the app's author — a community member can submit the app on your behalf using the same recipe.

Keep builds reproducible for this to work at all (see PLAN.md §6): the release workflow already sets
`vcsInfo.include = false` and disables ART baseline profile generation so a rebuild from source matches
our signed APK byte-for-byte, which is what lets F-Droid trust `AllowedAPKSigningKeys`.
