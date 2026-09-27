# FitGPX: design, build and deployment plan

This document explains **what** FitGPX is, **how** it's built, and **how** it gets to users. It's written
for contributors and for anyone deciding whether to trust the app with their activity data.

---

## 1. Product

### 1.1 Problem

Fitness devices record activities in Garmin's binary **FIT** format. Many tools that people want to use
with those activities, such as route planners, mapping apps, club websites, GIS software and older
analysis tools, only accept **GPX**. Today people convert through web services (which means uploading
their home location), desktop scripts, or by exporting one activity at a time from a vendor's website.

### 1.2 Goals

1. **Convert FIT → GPX on the phone**, quickly and faithfully, with no account and no upload.
2. **Batch first.** Converting 1 file and converting 1,000 (a full Garmin or Strava export) should both work.
3. **Trim.** Keep only part of an activity (a race inside a warm-up, a route without the drive to the trailhead).
4. **Privacy.** Make it easy to hide home and work before a file leaves the phone.
5. **Professional quality**: a polished Material 3 UI, a robust engine, tests, CI, signed releases, and a
   clean path into F-Droid.
6. **FOSS**: GPL-3.0-or-later, no proprietary dependencies, reproducible builds.

### 1.3 Non-goals (for 1.x)

- Being a training-analysis app (no charts beyond what trimming needs, no power curves).
- Uploading to third-party services directly. Sharing is delegated to the Android share sheet.
- Converting *to* FIT, or other formats (TCX, KML). These are possible later (see §9).
- Reading from devices over USB/Bluetooth. Users get files from their vendor app, a file manager or an export.

### 1.4 Users & key journeys

| Persona | Journey |
|---|---|
| **Rider with a club** | Shares a ride's GPX in a chat → *Share to FitGPX from Garmin Connect → Save GPX → Share*. |
| **Privacy-conscious runner** | Sets a 250 m privacy zone at home once; every export afterwards is clean. |
| **Racer** | Opens the activity, drags the trim handles to the start/finish of the race, saves the GPX. |
| **Data hoarder** | Downloads the Strava bulk export (5 GB zip) → *Select files → the zip → Convert 2,143 activities → Save to folder*. |
| **Route planner** | Converts a Garmin *course* FIT with turn cues into GPX with waypoints, simplified to 5 m. |

---

## 2. User experience

### 2.1 Screens

```
┌──────────── Home ────────────┐     ┌──────────── Editor ───────────┐
│ FitGPX            [+] [⚙] [⋮]│     │ ← Morning Ride         Reset  │
│ 12 activities · 842 km       │     │ ┌───────────────────────────┐ │
│ ┌──────────────────────────┐ │ tap │ │   map (OSM) with route,   │ │
│ │[route] Morning Ride      │ │ ──► │ │  kept part highlighted    │ │
│ │ Sat 27 Sep · Edge 540    │ │     │ └───────────────────────────┘ │
│ │ 42.2 km · 1:32 · ↑512 m  │ │     │ Distance  Moving  Elevation   │
│ │ ✓ Saved as ride.gpx      │ │     │ ┌ Trim ─────── [Trim idle] ┐ │
│ └──────────────────────────┘ │     │ │ [Elevation|Speed|HR]      │ │
│  …                           │     │ │ ▁▂▅▇█▆▃▂ profile w/handles │ │
│ [    Convert 12 activities  ]│     │ │ ●━━━━━━━━━━━━━━━━━━━━━━●  │ │
└──────────────────────────────┘     │ │ Start 0.4 km ‹›  End 41 km‹›│ │
        │ Convert                    │ └───────────────────────────┘ │
        ▼                            │ ┌ Privacy ──────────────────┐ │
┌── Save N GPX files ──────────┐     │ │ Hide first  ─○──── 200 m  │ │
│ 📁 Save to “GPX”             │     │ │ Hide last   ○───── Off    │ │
│ 📁 Choose another folder…    │     │ └───────────────────────────┘ │
│ 🗜 Save as a ZIP file…       │     │ [   Done   ] [  Save GPX   ]  │
│ ➤ Share…                     │     └───────────────────────────────┘
└──────────────────────────────┘
```

Other screens: **Settings** (output, GPX content, track processing, privacy, appearance), **Privacy zones**
(map with long-press to add, plus manual coordinates) and **About** (version, source, licenses, attributions).

### 2.2 Design principles

- **One primary action per screen.** Home = *Convert*; Editor = *Save GPX*.
- **Nothing destructive.** Edits never modify the source file. *Reset* restores the original, and a
  per-activity edit is stored on the list item until the list is cleared.
- **Honest status.** Every item shows *Ready / Converting / Saved as … / No GPS / Can't read*, and damaged
  files that were recovered get a *Recovered* badge.
- **Works without a map.** The map is progressive: with tiles off (or offline) the editor draws the route
  shape on a canvas, and trimming works the same.
- **Accessible.** 48 dp touch targets, content descriptions on every icon button, tabular numbers,
  dark theme and dynamic color, and support for the platform's font scale.

### 2.3 Visual language

Material 3 with a custom **deep-teal** brand scheme (primary `#006A63`) and a **warm orange route line**
(`#E8590C`) that stands out on OpenStreetMap's greens. Data series have fixed colors (elevation = teal,
speed = indigo, heart rate = pink) that work in both themes. The launcher icon is an adaptive route-to-pin
glyph with a monochrome layer for themed icons.

---

## 3. Architecture

### 3.1 Modules

```
fitgpx/
├── fit-core/   Pure Kotlin/JVM library. Zero dependencies. The entire conversion engine.
├── app/        Android app: Jetpack Compose UI, SAF storage, osmdroid map.
└── cli/        Desktop command-line converter (runnable fat jar).
```

Keeping the engine in a plain JVM module means it's **fast to test** (no emulator), **reusable** (CLI,
potential desktop/web ports) and **easy to audit**.

### 3.2 Engine pipeline (`fit-core`)

| Stage | Class | Responsibility |
|---|---|---|
| Unpack | `io.InputUnpacker` | Detects FIT / gzip / zip by magic bytes; **streams** nested archives; bounds entry size, total size, entry count and nesting depth (zip-bomb safe). |
| Decode | `fit.FitDecoder` | Binary FIT: 12/14-byte headers, header & file CRC-16, definition messages (LE/BE), compressed timestamp headers with rollover, developer fields (skipped by declared size), chained files, misaligned fields, **lenient recovery** of truncated/unsized files. |
| Interpret | `fit.ActivityReader` | Builds `Activity`: records → `TrackPoint` (semicircles → degrees, enhanced altitude/speed preferred, invalid sentinels → null, `0,0` placeholders dropped, crash-garbage timestamps rejected), sessions (multisport), laps, timer pauses, course + course points, UTC offset. |
| Edit | `track.TrackProcessor` | Applies an `EditSpec`: trim range → hide start/end by distance → GPS-spike filter → split per session → privacy-zone removal (splitting segments) → split at pauses → Ramer-Douglas-Peucker simplification. |
| Measure | `track.TrackStats` | Distance (haversine), elapsed/moving time, elevation gain/loss with 2 m hysteresis, HR/power/cadence averages, bounds. |
| Write | `gpx.GpxWriter` | Streams GPX 1.1 with Garmin TrackPointExtension v1 (`atemp`, `hr`, `cad`) and PowerExtension v1, locale-independent number formatting, XML-safe escaping, metadata bounds, waypoints with symbols. |
| Facade | `FitToGpx` | `read`, `convert`, file-name templates (`{name} {date} {time} {sport} {device}`), unique names, human titles ("Morning Ride"). |

**Memory model.** The decoder works on one file's bytes (FIT files are small, typically 50 KB to 5 MB) and emits
messages through a callback. Archives are never fully loaded: exactly one FIT payload is in memory at a
time. GPX is streamed to the output, so a 10-hour, 1 Hz ride (36,000 points) uses a few MB.

### 3.3 App (`app`)

```
MainActivity ── NavHost ──┬── HomeScreen ◄── HomeViewModel ─┐
                          ├── EditorScreen ◄── EditorViewModel ─┤
                          ├── SettingsScreen / PrivacyZones ◄── SettingsViewModel ─┤
                          └── AboutScreen                                    │
                                                                             ▼
                               AppContainer (manual DI) ── QueueRepository ── Storage (SAF sinks)
                                                        └─ SettingsRepository (DataStore)
```

- **QueueRepository** owns the list of activities (`QueueItem`: source URI + payload index, status,
  summary, per-item `ItemEdit`). Import streams each document once and stores only a **summary** (stats
  plus a ≤150-point preview), so thousands of items fit in memory. Conversion groups items by document
  and decompresses each archive **once**.
- **Storage** uses the **Storage Access Framework** only: `OpenMultipleDocuments`, `OpenDocumentTree`
  (with a persisted grant for the default folder) and `CreateDocument` for ZIPs. It never asks for a storage
  permission. There are three output sinks: *Folder* (keep-both or overwrite), *Zip*, and *Share*
  (FileProvider in cache).
- **Settings** live in Jetpack DataStore. They are backed up with Android backup, and nothing else is.
- **Editor** loads the full activity once, precomputes cumulative distance and a 2 m-simplified index
  list for drawing, and recomputes stats off the main thread (`mapLatest`) while handles move.
- **Intents**: `VIEW` for `.fit`/FIT MIME types, and `SEND`/`SEND_MULTIPLE` for FIT, zip and gzip, so "Share to
  FitGPX" works from Garmin Connect, file managers and email.

### 3.4 Key decisions

| Decision | Why |
|---|---|
| Clean-room FIT decoder instead of the Garmin FIT SDK | License compatibility with GPL and F-Droid, no binary blobs, and it only needs the ~5% of the profile that GPX uses. It's cross-checked against two independent decoders. |
| GPX 1.1 + Garmin TPX **v1** (not v2) | v1 is what Garmin Connect exports and what every consumer reads. v2 adds little (speed/course) and some tools ignore it. |
| osmdroid for maps | Apache-2.0, no API key, no Google Play Services, and it's accepted by F-Droid. |
| Manual DI, no Hilt | A small app. Fewer build plugins mean faster, reproducible builds. |
| No storage permission | SAF covers every use case and is the Play/F-Droid-friendly path. |
| Lenient decoding by default | Users' most valuable files are often the ones a device crashed while writing. |

---

## 4. Privacy & security

- **Data never leaves the device** unless the user shares it. There are no analytics, crash reporters,
  accounts or ads.
- The only network use is **map tiles** from OpenStreetMap, sent with an identifying User-Agent as the
  tile policy requires. It can be disabled in Settings, and the tile cache is app-private and capped at
  100 MB.
- **Untrusted input hardening**: every length is bounds-checked in the decoder, archives are capped at
  64 MB per payload, 8 GB total, 100,000 entries and 3 levels of nesting, and malformed input yields a
  per-file error instead of a crash.
- Privacy zones also suppress **waypoints** (laps, course points) inside the zone. Zones split the track,
  so no segment ever connects across a hidden area.
- Release signing keys live only in GitHub Actions secrets. See [RELEASING.md](RELEASING.md).

---

## 5. Quality: testing strategy

| Layer | What | Where |
|---|---|---|
| Decoder unit tests | Synthetic files made by a test-only **FIT encoder**: endianness, compressed timestamps + rollover, dev fields, invalid values, 12-byte headers, chained files, truncation, unsized headers, CRC errors, undefined local messages, misaligned fields | `fit-core/src/test/.../FitDecoderTest.kt` |
| Real-world corpus | 17 files from Garmin, Wahoo, Coros and the Strava app, including damaged ones. Counts are cross-checked with **fitdecode** and the **Garmin FIT SDK**, and 4 files are compared **point by point** against golden CSVs produced by fitdecode | `CorpusTest.kt`, `resources/golden/` |
| GPX validity | Every output variant is validated against the **GPX 1.1 XSD + Garmin TPX v1 + PowerExtension v1** schemas | `GpxSchema.kt`, `GpxWriterTest.kt` |
| Processing | Trim, hide start/end, privacy zones, pauses, multisport, spikes, simplification, stats | `TrackProcessorTest.kt` |
| Input | gzip, nested zip, junk entries, zip-bomb and entry limits, random access by index | `InputAndNamingTest.kt` |
| App logic | Settings encoding, edit merging, formatting | `app/src/test/.../AppLogicTest.kt` |
| UI | **Roborazzi** screenshot tests render every screen (light and dark) on the JVM. CI uploads them for review | `ScreenshotTest.kt` |
| Static | Android Lint (fails the build on errors), Kotlin warnings-as-errors in `fit-core` and `cli` | CI |
| Smoke | CI runs the CLI jar over the whole fixture corpus | CI |
| Device | An **Android 14 emulator** in CI runs end-to-end conversion tests (share, ZIP, trim) and walks the real app through import → settings → editor with live map → save sheet, publishing screenshots of each step | `app/src/androidTest/` |

**Manual test checklist before a release:** import from Garmin Connect share, a Strava bulk export zip,
a folder with 100+ files, trim + privacy zone + save to folder / zip / share, rotate during conversion,
dark mode, font scale 200%, TalkBack on the editor, airplane mode (map fallback).

---

## 6. Build

- **Gradle 9.7** (wrapper, checksum-pinned), **AGP 9.3** with built-in Kotlin, **Kotlin 2.4**, version catalog
  in `gradle/libs.versions.toml`, configuration cache and build cache on.
- `compileSdk 37`, `targetSdk 36`, `minSdk 26` (Android 8.0, which covers ~97% of active devices).
- Release builds use **R8** full mode with resource shrinking. The APK is ~5 MB, and there's no native code, so one
  APK covers every ABI.
- **Reproducible-build hygiene** for F-Droid: `dependenciesInfo` blob disabled, VCS info disabled,
  version code/name literal in `app/build.gradle.kts`, no build timestamps, reproducible jar ordering.
- Debug builds use the `.debug` application-ID suffix so they install next to release builds.

---

## 7. Continuous integration & delivery

### 7.1 CI (`.github/workflows/ci.yml`, every push and PR)

1. `:fit-core:test`: engine tests, schema validation and the golden corpus
2. `:cli:fatJar`, then a smoke run over all fixtures
3. `:app:testDebugUnitTest` and `:app:recordRoborazziDebug` (screenshots uploaded as an artifact)
4. `:app:lintDebug`
5. `:app:assembleDebug :app:assembleRelease` (R8 runs on every PR, so shrinker bugs surface early)
6. Artifacts: debug APK, CLI jar, screenshots, test and lint reports

Dependabot keeps Gradle dependencies (grouped) and Actions up to date.

### 7.2 Release (`.github/workflows/release.yml`, on tag `vX.Y.Z`)

1. Verifies the tag matches `versionName`.
2. Decodes the signing keystore from secrets, runs the tests, and builds the **signed** APK and CLI jar.
3. Publishes a GitHub Release with the notes taken from `CHANGELOG.md`, `SHA256SUMS.txt`, and the signing
   certificate fingerprint for verification.

Step-by-step instructions, including creating the keystore, are in [RELEASING.md](RELEASING.md).

### 7.3 Distribution

| Channel | Plan |
|---|---|
| **GitHub Releases** | Primary channel from v1.0.0. Signed APK and CLI. |
| **Obtainium** | Works out of the box with GitHub Releases (no extra work). |
| **F-Droid** | After 1.0 is stable: submit to `fdroiddata` using the fastlane metadata in `fastlane/metadata/android/`. Aim for **reproducible builds** so F-Droid ships our signature. |
| **IzzyOnDroid** | Optional fast track while the F-Droid review is pending. |
| **Google Play** | Optional. Needs a developer account, the [privacy policy](../PRIVACY.md) (already written) and the data-safety form ("no data collected"). Use the same keystore as the upload key, or Play App Signing. |

### 7.4 Versioning

Semantic versioning. `versionCode = MAJOR*10000 + MINOR*100 + PATCH` (1.2.3 → 10203). Every user-visible
change gets a line in `CHANGELOG.md` under *Unreleased*.

---

## 8. Milestones

| Milestone | Scope | Exit criteria |
|---|---|---|
| **M1: Engine** ✅ | FIT decoder, GPX writer, processing, CLI, tests | Corpus matches independent decoders, GPX schema-valid |
| **M2: App MVP** ✅ | Import (files/folder/share/zip/gz), batch convert to folder/zip/share, settings | CI green, APK installs, screenshots reviewed |
| **M3: Editor** ✅ | Map + profile trim, nudge, trim idle, hide start/end, privacy zones | Manual checklist passes |
| **M4: 1.0 release** | ✅ Emulator E2E tests + signed `v1.0.0-beta.1` pre-release. Next: testing on 3+ physical devices, then `v1.0.0` | Tagged `v1.0.0` on GitHub |
| **M5: Reach** | F-Droid submission, translations via Weblate, IzzyOnDroid | Listed in F-Droid |

---

## 9. Roadmap (post-1.0)

- **UNA Watch sync** (in progress on `feature/una`, see [UNA.md](UNA.md)): Bluetooth File Transfer
  client, sync screen, hardware testing, then merge.

- **More formats**: TCX and KML/GeoJSON output, GPX *route* (`<rte>`) output for navigation devices.
- **Split & merge**: split an activity at a point, and merge several into one GPX.
- **Persist the queue** across process death, and add a *recent conversions* history.
- **Quick Settings tile / widget**: "convert the newest file in Downloads".
- **Photo/timestamp tools**: export a CSV of positions for geotagging photos.
- **Translations** through Weblate, and right-to-left layout checks.
- **Instrumented tests** on an emulator in CI (SAF flows, share intents).
- **Baseline profile** for faster startup.

---

## 10. Risks & mitigations

| Risk | Mitigation |
|---|---|
| A device writes FIT in a way the decoder doesn't expect | Lenient decoding, per-file errors, a bug template asking for the file, and the corpus test grows with every reported file. |
| Very large exports exhaust memory | Streaming unpacker, summaries instead of full tracks in the queue, and per-payload size caps. |
| OSM tile policy / availability | Identifying User-Agent, on-disk cache, map can be disabled, and a canvas fallback. |
| Lost signing key breaks updates | Keystore backed up offline by the maintainer (see RELEASING.md). F-Droid reproducible builds reuse our signature. |
| Dependency churn | Version catalog, grouped Dependabot PRs, and CI on every PR. |
