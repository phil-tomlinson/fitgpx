# UNA Watch integration

FitGPX can copy activity recordings straight off a [UNA Watch](https://unawatch.com) over Bluetooth and
convert them like any other FIT file. UNA recordings are standard FIT files, so everything else in FitGPX
(trimming, privacy zones, batch export) works on them unchanged.

## How it works

```
UNA Watch ──BLE File Transfer Service──► FitGPX (una-fts) ──► files/una/<App>/*.fit ──► conversion list
```

1. **Connect.** FitGPX pairs with the watch if needed (it requires a bonded, encrypted link), asks for a
   247-byte MTU, and subscribes to the File Transfer Service (`0xFEBB`, Raw Transfer `ADAF0002`).
2. **Find recordings.** UNA activity apps write `/Apps/<App>/Activity/<YYYYMM>/activity_<timestamp>.fit`.
   FitGPX lists `/Apps`, each app's `Activity` folder and its month folders. That takes a few round trips,
   with no walk of the whole file system.
3. **Copy what's new.** A file whose name and size match a previously synced copy is skipped. New files
   are read with the protocol-v5 **windowed reads** (4 KB bursts, reassembled by offset, with lost
   notifications re-requested from the first missing byte), then verified with the watch's **DIGEST**
   (CRC-32) and re-read once on mismatch. Version-4 watches fall back to classic stop-and-wait reads.
4. **Import.** Copies are kept in app-private storage (`files/una/`) and added to the list. FitGPX never
   writes to or deletes anything on the watch.

The protocol is implemented in the pure-Kotlin [`una-fts`](../una-fts) module and tested against a simulated
watch that drops notifications, sends short bursts and corrupts data (`FtsClientTest`). The Android
Bluetooth layer lives in `app/src/main/kotlin/…/una/`.

References: [UNA SDK](https://github.com/UNAWatch/una-sdk) (MIT): `Docs/BLE-File-Transfer-Service.md`,
`Docs/BLE-Services-Overview.md`, `Docs/FitFiles-Structure.md`.

## Permissions and privacy

- Android 12+: `BLUETOOTH_SCAN` (flagged `neverForLocation`) and `BLUETOOTH_CONNECT`.
- Android 8–11: `BLUETOOTH`, `BLUETOOTH_ADMIN`, and location, which Android requires for any BLE scan there.
- Permissions are requested only when you open **Sync from UNA Watch**. Nothing about the watch leaves
  your phone.

## Trying it without a watch

Debug builds show **Try with a simulated watch** on the sync screen. It runs the real sync code against
an in-memory watch loaded with sample recordings (one encoded by the UNA SDK itself). The emulator tests
in CI use it too.

## Hardware test checklist

- [ ] Pair from the sync screen, and with a watch already paired in system settings or the UNA app
- [ ] Sync while the official UNA app is connected: does the watch accept the second connection?
- [ ] Sync several activities; open one in the editor and check time, HR and elevation
- [ ] Walk out of range mid-transfer, come back, sync again: no partial files in the list
- [ ] Sync again straight away: "No new activities"
- [ ] Note the transfer speed (KB/s) for the README

## Relationship to una-trailforks

FitGPX covers the phone side of a watch → Trailforks workflow without needing Trailforks API access:
sync from the watch, trim and apply privacy zones, then upload the GPX with Trailforks' manual
**Add Ridelog Using a GPS File** tool. The desktop `fitgpx-cli.jar` can do the same conversion step on a
computer.
