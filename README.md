# Manchester Approach

An offline Android air traffic control strategy game. Draw routes, assign altitude and speed, and sequence arrivals and departures around a simplified Manchester Airport terminal area.

New controller? Start with the [complete how-to-play guide](HOW_TO_PLAY.md), including a
step-by-step walkthrough of the first mission.

## Included gameplay

- Eight progressive missions plus an escalating endless shift
- Deterministic radar simulation with headings, routes, altitude, speed, separation, fuel, runway occupancy and go-arounds
- Arrival, departure, line-up and take-off clearances across both runway directions
- Touch route drawing, aircraft command panels, pause and simulation-speed controls
- Scoring, star ratings, unlock progression, local high scores and an exact command-replay save/continue system
- First-shift tutorial, high-contrast labels, label scaling, reduced motion, trails, audio and haptic settings

## Build

Requirements:

- JDK 21
- Android SDK 37 and Build Tools 36.0.0
- Android Studio Quail 2 or a compatible command-line installation

Run `./gradlew test` for JVM tests, `./gradlew assembleDebug` for an installable debug APK, or open the project in Android Studio. The game is landscape-first and supports Android 8.0 (API 26) and newer.

Release builds use code and resource shrinking. Local release APKs remain unsigned; signed Play
bundles are gated by the secret-safe CI process in [RELEASING.md](RELEASING.md).

## Aviation data notice

The airport layout is an intentionally simplified entertainment representation informed by the UK AIP snapshot dated 16 April 2026. It is not current operational data and must not be used for navigation or training. NATS and Manchester Airport do not endorse this game.
