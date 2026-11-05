# Debug APKs

Both point at the live backend, `https://sareemarket.duckdns.org`. Install with `adb install -r <file>`; a newer build installs over an older one (same package, same debug signature).

| File | What it is | Built from |
| --- | --- | --- |
| `saree-market-debug.apk` | **Latest.** Orders confirm when the customer pays; the seller adds courier and tracking, the customer is notified, can copy the number and taps Received; plus order photos, 50-photo uploads, Change login phone. **Needs the backend with migration V17 (live since PR #12 was deployed on 2026-11-05); it does not work against the older backend.** | `main` after PR #12 (also includes the stock-count fix: a saree with no pieces left counts as sold out) |
| `saree-market-debug-previous.apk` | **Older.** The v3 redesign before type and fabric were removed (still asks for both) | branch `ui-redesign-v3` at `361f0fd` |

Rebuild the latest with `cd android && ./gradlew assembleDebug -PapiBaseUrl=https://sareemarket.duckdns.org`, then copy `app/build/outputs/apk/debug/app-debug.apk` here.
