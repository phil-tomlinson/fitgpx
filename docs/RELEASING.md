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

After the first release, open a merge request on [fdroiddata](https://gitlab.com/fdroid/fdroiddata) with
`metadata/io.github.philtomlinson.fitgpx.yml`. F-Droid reads the description, screenshots and changelogs
from `fastlane/metadata/android/`. To let F-Droid ship *our* signature (so users can switch between
GitHub and F-Droid builds), add `AllowedAPKSigningKeys` with the certificate SHA-256 printed in the
release notes, and keep builds reproducible (see PLAN.md §6).
