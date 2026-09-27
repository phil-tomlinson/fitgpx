# Credits

FitGPX stands on the work of others. Thank you.

## Protocols and specifications

| What | Used for | License / terms |
|---|---|---|
| **FIT protocol** by Garmin ([developer.garmin.com/fit](https://developer.garmin.com/fit/)) | The FIT decoder in `fit-core` is a clean-room implementation of the public protocol description. No Garmin SDK code is included. | Public protocol documentation |
| **GPX 1.1** by TopoGrafix ([topografix.com/gpx.asp](https://www.topografix.com/gpx.asp)) | Output format. The schema is used in tests to validate every file FitGPX writes. | Freely usable schema |
| **Garmin TrackPointExtension v1 / PowerExtension v1** | GPX extensions for heart rate, cadence, temperature and power | Published XML schemas |
| **UNA Watch SDK** ([github.com/UNAWatch/una-sdk](https://github.com/UNAWatch/una-sdk)) by UNA Watch Ltd | The BLE File Transfer Service documentation, FIT file layout and storage paths behind UNA sync. Its `FitWriter` generated the `una-watch-mtb.fit` test fixture. | MIT |
| **Adafruit CircuitPython BLE File Transfer** ([github.com/adafruit/Adafruit_CircuitPython_BLE_File_Transfer](https://github.com/adafruit/Adafruit_CircuitPython_BLE_File_Transfer)) | The base protocol that UNA's File Transfer Service extends | MIT |

## Libraries shipped in the app

| Library | License |
|---|---|
| [Kotlin standard library](https://kotlinlang.org) and [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Apache-2.0 |
| [Jetpack Compose, Material 3 and AndroidX](https://developer.android.com/jetpack) | Apache-2.0 |
| [Material Design icons](https://fonts.google.com/icons) | Apache-2.0 |
| [osmdroid](https://github.com/osmdroid/osmdroid) | Apache-2.0 |

## Data

- Map tiles and map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), available
  under the Open Database License (ODbL). Tiles are served by the OpenStreetMap Foundation under its
  [tile usage policy](https://operations.osmfoundation.org/policies/tiles/).

## Testing and verification

| Project | Used for | License |
|---|---|---|
| [python-fitparse](https://github.com/dtcooper/python-fitparse) | Real-world FIT test files in `fit-core/src/test/resources/fit/` (license included alongside) | MIT |
| [fitdecode](https://github.com/polyvertex/fitdecode) | Independent decoder that produced the golden point-by-point comparison files | MIT |
| [Garmin FIT Python SDK](https://github.com/garmin/fit-python-sdk) | Cross-checking decoded values during development (not distributed) | Garmin FIT SDK license |
| [GPSBabel](https://github.com/GPSBabel/gpsbabel) | Source of the copies of the GPX 1.1 and TrackPointExtension v1 schemas used in tests | Schemas by TopoGrafix / Garmin |
| [JUnit 4](https://junit.org/junit4/), [Robolectric](https://robolectric.org), [Roborazzi](https://github.com/takahirom/roborazzi) | Unit and screenshot tests | EPL-1.0, MIT, Apache-2.0 |

## Build and project setup

- [Gradle](https://gradle.org) and its wrapper scripts (Apache-2.0).
- Build configuration patterns and dependency versions were informed by Google's
  [Now in Android](https://github.com/android/nowinandroid) and
  [Compose samples](https://github.com/android/compose-samples) (Apache-2.0).
- F-Droid store listing follows the [fastlane metadata](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/) conventions.

## Trademarks

FIT and Garmin are trademarks of Garmin Ltd. UNA is a trademark of UNA Watch Ltd. Strava, Wahoo, COROS,
Hammerhead, Zwift and other product names mentioned are trademarks of their respective owners. FitGPX is
independent and not affiliated with or endorsed by any of them.
