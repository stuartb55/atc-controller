# How to play Manchester Approach

Manchester Approach is a simplified, offline air-traffic-control strategy game. Your job is to
route every aircraft to its assigned runway or exit, keep aircraft separated, and finish all
scheduled movements before the shift timer expires. It is entertainment, not real-world ATC
training or navigation software.

## The basic game loop

1. Watch for a new callsign on the radar.
2. Tap the aircraft symbol or its callsign label to open the command panel.
3. Give it a safe route, altitude and speed.
4. For an arrival, set up its approach and issue **Clear to land**. For a departure, wait for a
   clear runway, issue **Clear for take-off**, then route it to its assigned exit.
5. Keep checking the rest of the radar while that aircraft follows its instructions.

A mission completes automatically only after every required arrival has landed and every required
departure has exited. **End shift & debrief** ends an active attempt early and does not count as a
mission completion.

## First mission: First Contact

This mission contains three arrivals for runway 23R: NORTH 201, CLOUD 314 and VECTOR 122. They
appear at different times, so the radar can briefly be empty at the start.

Use this reliable sequence:

1. Finish or skip the four tutorial cards by tapping **Next**, then **Take control**.
2. Leave simulation speed at **1×** while learning.
3. When NORTH 201 appears, tap its aircraft symbol or callsign label.
4. In the right-hand command panel, tap **Set up approach**. This creates a short final route,
   selects ground level as the target altitude, and selects a safe landing speed.
5. Tap **Clear to land — 23R**. A landing clearance may be issued before the aircraft reaches final;
   it will land once it is aligned, low and slow enough.
6. Repeat those three actions—select, **Set up approach**, **Clear to land**—when CLOUD 314 and
   VECTOR 122 appear.
7. Let the simulation run. The mission completes after VECTOR 122 finishes its landing roll.

Do not tap **End shift & debrief** to try to finish the mission. That is an early-exit control. If
two arrivals look close, keep one at least 1,000 ft above the other until their paths separate, or
draw a longer route for the following aircraft before setting up its final approach.

## Radar and command controls

- **Select aircraft:** Tap the aircraft symbol or its callsign label. Tap × in the command panel to
  close the strip.
- **Draw a route:** Select an aircraft, then drag across the radar. The route is committed when you
  lift your finger. Use deliberate, widely spaced turns; aircraft turn gradually rather than
  pivoting instantly.
- **Set up approach:** For an arrival, creates a flyable final route to its assigned runway and sets
  safe landing altitude and speed targets. You still decide when to clear it to land.
- **Altitude − / +:** Changes target altitude in 500 ft steps. The aircraft climbs or descends
  gradually.
- **Speed − / +:** Changes target speed in 10 kt steps. Different aircraft classes accelerate,
  slow and turn at different rates.
- **Clear to land:** Authorizes an arrival to use its assigned runway. It does not teleport or snap
  the aircraft onto final.
- **Go around:** Cancels the landing attempt, turns the aircraft onto runway heading and sends it
  toward 3,000 ft. Re-route it before trying again. Manual and automatic go-arounds reduce score.
- **Clear for take-off:** Starts a departure's take-off roll if its assigned runway is free.
- **1× / 2×:** Changes simulation speed. Commands, fuel and mission time all continue at the chosen
  rate.
- **Pause:** Stops simulation time. On supported layouts, the command panel remains available after
  resuming.

The small triangle markers around the edge are fictional entry/exit fixes. Cyan `I-runway` markers
and the dashed line show the approach direction. Aircraft labels show callsign, altitude/flight
level, trend and speed; label detail can be adjusted in Settings.

## Separation, warnings and failure

Keep aircraft at least 3 nautical miles apart horizontally or 1,000 ft apart vertically. An amber
triangle/dashed connection is a predicted conflict. A red diamond/double connection means
separation is already lost. Tap either callsign in the warning banner to select it; arrow buttons
cycle through multiple conflicts.

A shift can fail because of:

- three separation strikes (the mission objective allows no more than two);
- a collision or runway incursion;
- an aircraft leaving controlled airspace at the wrong place;
- fuel exhaustion; or
- the mission timer expiring.

The three dots in the top bar are the strike counter. Route aircraft early, use altitude before
paths cross, and do not clear a departure while another aircraft occupies its runway.

## Arrivals

For the simplest arrival, select it, tap **Set up approach**, then **Clear to land**. In busier
missions, first create spacing with altitude, speed or a short delay route. Once the preceding
aircraft has landed and cleared the runway, set up and clear the next arrival.

An approach that reaches the threshold too high, too fast or badly aligned automatically goes
around. Select it again, assign a route back toward final, descend and slow, then issue a new
landing clearance. Avoid sending several aircraft down the same final with less than roughly a
minute between them.

## Departures

Departures spawn holding short of their assigned runway. Select one and tap **Clear for take-off**
only when the runway is unoccupied and no arrival is about to touch down. Its route initially leads
to its assigned edge exit. After take-off, verify that it continues toward that fix; a wrong exit
costs points and leaving elsewhere can fail the shift.

Light aircraft use the runway briefly and turn quickly. Heavy aircraft accelerate, turn and clear
the runway more slowly, so allow a larger gap.

## Missions and progression

- **1 — First Contact:** selection, routing and a basic arrival flow.
- **2 — Level Headed:** use 1,000 ft altitude layers where routes converge.
- **3 — Slow and Steady:** use speed to build an orderly arrival sequence.
- **4 — Cleared to Land:** judge runway availability and recover with go-arounds.
- **5 — Opening the Flow:** introduce departures and assigned exits.
- **6 — Mind the Runway:** interleave arrivals and departures without overlapping runway use.
- **7 — Mixed Picture:** account for light, jet and heavy performance differences.
- **8 — Parallel Pressure:** manage assigned traffic on both 23R and 23L.
- **Endless sector:** seeded stages that become progressively busier; it unlocks after the first
  five authored missions have been completed.

Completing an authored mission unlocks the next one. Stars depend on score. Safe movements, short
efficient routes and prompt handling add points; conflicts, excess route distance, go-arounds and
missed exits reduce them. A low-star completion still unlocks the next mission.

## Saving, settings and recovery

Leaving or backgrounding an active shift pauses and saves a deterministic command replay. Use
**Continue** on the home screen to reconstruct it. Settings include audio levels, haptics, trails,
reduced motion, high contrast, label size, label decluttering and pause-on-focus-loss.

If the first mission still uses the old tutorial text mentioning `EXS72M`, the installed APK
predates the playability update. Build and reinstall the current debug APK with
`./gradlew assembleDebug`, or install a newer release, then restart **First Contact**.
