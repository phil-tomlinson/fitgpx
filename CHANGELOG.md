# Changelog

All notable changes to FitGPX are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.1.0-beta.2] - 2026-09-27

### Fixed
- UNA sync stopped with "The watch stopped responding (listing /Apps/Alarm/Activity)". FitGPX now only opens
  folders the watch reports, skips any that don't answer, and finds `.fit` files anywhere on the watch.

### Added
- "Share log" on the UNA sync screen: a plain-text record of what the watch reported, for troubleshooting.

## [1.1.0-beta.1] - 2026-09-27

Beta for testing UNA Watch sync on real hardware. Installs as an update over 1.0.0-beta.1. Open the list screen and tap **Sync from a UNA Watch** (or + → From UNA Watch).

### Added
- Sync activities straight from a UNA Watch over Bluetooth (new files only, checksum-verified).
- Activities are named from their sub-sport: Mountain Bike Ride, Gravel Ride, Trail Run.
- UNA Watch recordings show "Una" as the device.

## [1.0.0-beta.1] - 2026-09-27

First public beta.

### Added
- Convert FIT activities and courses to GPX 1.1 with elevation, time, heart rate, cadence, power and
  temperature (Garmin TrackPointExtension/PowerExtension).
- Batch conversion of files, folders, Garmin Connect `.zip` exports and Strava bulk exports (`.fit.gz`).
- Save to a remembered folder, save as ZIP, or share to any app. Open or share `.fit` files into FitGPX.
- Trim editor with map, elevation/speed/heart-rate profile, draggable handles, point nudging and
  *Trim idle*.
- Privacy zones, hide start/end distance, and optional removal of timestamps and sensor data.
- GPS spike removal, split at pauses, one track per sport in multisport, simplification and coordinate
  precision options.
- Recovery of GPS data from truncated or unclosed FIT files.
- Material 3 design with dark theme, dynamic color and metric/imperial units.
- Desktop command-line converter (`fitgpx-cli.jar`).
