# FIT fixtures

- Most files come from [python-fitparse](https://github.com/dtcooper/python-fitparse) (MIT, see
  `LICENSE-python-fitparse.txt`): real recordings from Garmin, Wahoo, Coros, Zwift and the Strava app,
  including deliberately damaged ones.
- `una-watch-mtb.fit` is a synthetic mountain-bike ride encoded by the
  [UNA Watch SDK](https://github.com/UNAWatch/una-sdk)'s own FIT encoder (MIT). Regenerate it with
  `una-watch-mtb.gen.cpp`:
  ```sh
  git clone https://github.com/UNAWatch/una-sdk
  g++ -std=c++17 -I una-sdk/Libs/Header -I una-sdk/Tests/Host/support una-watch-mtb.gen.cpp \
      una-sdk/Libs/Source/Fit/FitWriter.cpp una-sdk/Libs/Source/Fit/FitCrc.cpp -o gen && ./gen
  ```
