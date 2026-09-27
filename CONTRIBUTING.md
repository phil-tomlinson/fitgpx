# Contributing to FitGPX

Thanks for helping! FitGPX is a small, focused project, and contributions of every size are welcome.

## Reporting a file that doesn't convert

This is the most useful thing you can do. Open a [bug report](https://github.com/phil-tomlinson/fitgpx/issues/new/choose)
and attach the file (zip it first, because GitHub doesn't accept `.fit` directly). FIT files contain your GPS
track, so remove anything private first, or record a short test activity that reproduces the problem.
With your permission, the file can be added to the test corpus so the bug never comes back.

## Development

- JDK 17+, Android Studio (latest stable) or the Android SDK command-line tools.
- The conversion engine lives in `fit-core/` and has **no Android dependency**: most work can be done and
  tested with `./gradlew :fit-core:test`.
- Run `./gradlew :fit-core:test :app:testDebugUnitTest :app:lintDebug` before opening a PR.
- If you change UI, re-record screenshots with `./gradlew :app:recordRoborazziDebug` and include the
  updated PNGs so reviewers can see the change.

### Code style

- Kotlin official style (`kotlin.code.style=official`), 4-space indent, max ~140 columns.
- Every source file starts with the SPDX header:
  ```kotlin
  /*
   * SPDX-FileCopyrightText: 2026 FitGPX contributors
   * SPDX-License-Identifier: GPL-3.0-or-later
   */
  ```
- UI strings go in `app/src/main/res/values/strings.xml`, never hard-coded, so they can be translated.
- Prefer small, reviewable PRs with a clear description and a line in `CHANGELOG.md` for user-visible changes.

### Adding a FIT fixture

Put the file in `fit-core/src/test/resources/fit/` (≤ 1 MB, with its license or the reporter's permission),
add an `Expect(...)` line to `CorpusTest.kt`, and, when it helps, a golden `*.points.csv` produced with an
independent decoder (see `resources/golden/README.md`).

## Translations

Translations are welcome. Copy `app/src/main/res/values/strings.xml` to `values-<lang>/strings.xml`
and translate the text (not the `name` attributes). A Weblate project will be set up after 1.0.

## License

By contributing you agree that your contributions are licensed under the GPL-3.0-or-later license of this
project.
