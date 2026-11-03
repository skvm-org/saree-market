# Debug APKs

Both point at the live backend, `https://sareemarket.duckdns.org`. Install with `adb install -r <file>`; a newer build installs over an older one (same package, same debug signature).

| File | What it is | Built from |
| --- | --- | --- |
| `saree-market-debug.apk` | **Latest.** Order photos, customer notifications for order updates, 50-photo uploads compressed on the phone, Change login phone | `main` after PRs #10 and #11 |
| `saree-market-debug-previous.apk` | **Older.** The v3 redesign before type and fabric were removed (still asks for both) | branch `ui-redesign-v3` at `361f0fd` |

Rebuild the latest with `cd android && ./gradlew assembleDebug -PapiBaseUrl=https://sareemarket.duckdns.org`, then copy `app/build/outputs/apk/debug/app-debug.apk` here.
