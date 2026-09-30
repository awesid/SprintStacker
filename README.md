# Sprint Stacker

An Android focus timer that builds a tower. Finish a sprint and a square block drops onto the stack. Give up early, or leave the app, and a malformed penalty block lands instead and the tower starts to lean. Lean too far and the top of the tower topples.

Everything runs on the device. There is no account and no server.

## Rules

| Preset | Points for finishing | Block |
| --- | --- | --- |
| 5m | 300 | large square (1.0) |
| 3m | 180 | medium square (0.8) |
| 1m | 60 | small square (0.6) |
| 10s (test) | 10 | extra small square (0.45) |

- Points are 1 per second focused. Giving up keeps half of the whole seconds you managed.
- Penalty blocks are scaled from the preset's square, with a random size and a random sideways offset.
- Leaving the app mid-sprint counts as giving up: pressing Home, switching apps, swiping the app away from Recents, or Android killing the process. Locking the screen does not count, and a sprint that finishes while the phone is locked is completed normally.
- Balance: for every block, the center of mass of everything above it is compared with its edges. The meter shows the worst ratio. At 70% the app warns and offers a rewarded "Stabilize". Past 100% the blocks above that point start to tip.
- When the tower tips, the player can watch a rewarded ad to stabilize it (every block slides back to the center) or to undo the last penalty block. Otherwise the blocks fall and their points are lost.

## Ads

- Rewarded video: optional, offered when the tower leans past 70% or starts to tip.
- Interstitial: after every 2nd completed sprint has settled, and after Clear Board. Never during a sprint or while the tower is tipping.
- Consent: Google's User Messaging Platform shows its form on launch where required (EEA and UK) before any ad request. Settings shows a "Privacy options" entry whenever UMP says one is required.

Debug builds always use Google's test ad units. Release builds read real IDs from Gradle properties and fall back to the test units if they are missing:

```properties
# ~/.gradle/gradle.properties (never commit real IDs)
ADMOB_APP_ID=ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy
ADMOB_INTERSTITIAL_ID=ca-app-pub-xxxxxxxxxxxxxxxx/zzzzzzzzzz
ADMOB_REWARDED_ID=ca-app-pub-xxxxxxxxxxxxxxxx/wwwwwwwwww
PRIVACY_POLICY_URL=https://your-domain.example/privacy
```

## Play Store checklist

- In Play Console, answer "Yes, my app contains ads".
- Host a privacy policy that names Google AdMob and its use of device identifiers for ads and analytics. Link it in the store listing and set `PRIVACY_POLICY_URL` so Settings opens it.
- Use real ad unit IDs only in release builds.

## Project layout

- `core/`: plain Kotlin game engine (tower physics, scoring, sprint rules). No Android dependencies, fully unit tested.
- `app/`: the Android app. Jetpack Compose UI, Room for local storage, AdMob and UMP.
  - `game/GameViewModel.kt`: game state, sprint timer, app-leave handling, persistence.
  - `ui/TowerCanvas.kt`: the playfield and its drop, wobble and fall animations.
  - `ads/`: consent and ad loading.

## Building

Requires JDK 17 and the Android SDK (API 35).

```sh
./gradlew :core:test          # engine tests
./gradlew :app:assembleDebug  # debug APK in app/build/outputs/apk/debug/
```

GitHub Actions runs both on every push and pull request and uploads the debug APK as a build artifact.
