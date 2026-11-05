# Debug APKs

Both point at the live backend, `https://sareemarket.duckdns.org`. Install with `adb install -r <file>`; a newer build installs over an older one (same package, same debug signature).

| File | What it is | Built from |
| --- | --- | --- |
| `saree-market-debug.apk` | **Latest.** Profile pictures (upload from the gallery or camera, change, remove; a seller's picture shows on catalog cards, the item page, the shop page, My sellers and notifications; a customer's on the seller's order cards); orders confirm when the customer pays, the seller adds the tracking number, the customer copies it and taps Received; a saree on an order opens its item page; the delivery address can be edited at checkout and changed on the payment screen; a saree with no pieces left counts as sold out. **Needs the backend from PRs #12, #13 and #14 (live since 2026-11-05); it does not work against the older backend.** | `main` after PR #14 |
| `saree-market-debug-previous.apk` | **Older.** The v3 redesign before type and fabric were removed (still asks for both) | branch `ui-redesign-v3` at `361f0fd` |

Rebuild the latest with `cd android && ./gradlew assembleDebug -PapiBaseUrl=https://sareemarket.duckdns.org`, then copy `app/build/outputs/apk/debug/app-debug.apk` here.
