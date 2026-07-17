# Manchester Approach

An offline Android air traffic control strategy game. Issue heading, level, speed and waypoint commands while sequencing arrivals and departures across two deterministic sector packs.

New controller? Start with the [complete how-to-play guide](HOW_TO_PLAY.md), including a
step-by-step walkthrough of the first mission.

## Included gameplay

- Twelve progressive Manchester missions and a four-mission original fictional coastal campaign
- Pack-aware custom, Daily and escalating endless shifts with checksummed offline share codes
- Deterministic routes, wind drift, reduced visibility, wake spacing, fuel, holds, handoffs and runway occupancy
- Runway/approach assignment, line-up, clearance cancellation, runway crossing, take-off, landing and go-around controls
- Authored runway changes, priorities, closures, rejected take-offs and equipment outages
- Live objectives, traffic horizon, star forecast, flight strips, event feed, detailed debrief and verified replay
- Local career/service records, Training Academy, accessibility controls, audio and haptic settings

## Build

Requirements:

- JDK 21
- Android SDK 37 and Build Tools 36.0.0
- Android Studio Quail 2 or a compatible command-line installation

Run `./gradlew test` for JVM tests, `./gradlew assembleDebug` for an installable debug APK, or open the project in Android Studio. The game is landscape-first and supports Android 8.0 (API 26) and newer.

Release builds use code and resource shrinking. Local release APKs remain unsigned; signed Play
bundles are gated by the secret-safe CI process in [RELEASING.md](RELEASING.md).

## Aviation data

The Manchester layout and named fixes are informed by the UK AIP snapshot dated 16 April 2026. Harbour Approach is an original crossing-runway layout. The in-app game-use notice is available in Settings.
