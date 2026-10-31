# Debug APKs

Both point at the live backend, `https://sareemarket.duckdns.org`. Install with `adb install -r <file>`; a newer build installs over an older one (same package, same debug signature).

| File | What it is | Built from |
| --- | --- | --- |
| `saree-market-debug.apk` | **Latest.** The v3 redesign: new item page, catalog cards, seller side, follow "+" on cards, launcher-icon logo | branch `ui-redesign-v3` |
| `saree-market-debug-previous.apk` | **Older.** Neutral colours and the Sarees tab, as it was on `main` | `main` at `4bb7d64` (2026-10-29) |

Rebuild the latest with `cd android && ./gradlew assembleDebug -PapiBaseUrl=https://sareemarket.duckdns.org`, then copy `app/build/outputs/apk/debug/app-debug.apk` here.
