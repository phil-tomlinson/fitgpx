# Golden point files

Each `*.points.csv` was produced by an **independent** decoder ([fitdecode](https://github.com/polyvertex/fitdecode))
from the matching file in `../fit/`, so the tests check FitGPX against a second implementation rather
than against itself.

Columns: `unix_seconds,lat,lon,elevation_m,heart_rate,cadence,power,speed_mps,temperature_c`
(empty = not recorded; records without a position, and `0,0` placeholder fixes, are omitted).

`strava-android-app-201.10-b1218918.fit.points.csv` has its final row removed on purpose: that record
carries a timestamp decades in the future written by a crashing app, which FitGPX discards.
