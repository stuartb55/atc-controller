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

1. Follow the action-gated Academy prompts, or skip the lesson and take control.
2. Leave simulation speed at **1×** while learning.
3. When NORTH 201 appears, tap its aircraft symbol or callsign label.
4. In the right-hand command panel, tap **Set up approach**. This creates a sequence of gentle
   vectors onto final, using a suitable named fix when one lies on the transition. It also selects
   ground level as the target altitude and a safe landing speed.
5. Tap **Clear to land — 23R**. A landing clearance may be issued before the aircraft reaches final;
   it will land once it is aligned, low and slow enough.
6. Repeat those three actions—select, **Set up approach**, **Clear to land**—when CLOUD 314 and
   VECTOR 122 appear.
7. Let the simulation run. The mission completes after VECTOR 122 finishes its landing roll.

Do not tap **End shift & debrief** to try to finish the mission. That is an early-exit control. If
two arrivals look close, keep one at least 1,000 ft above the other until their paths separate, or
send the following aircraft via another named waypoint before setting up its final approach.

## Radar and command controls

- **Select aircraft:** Tap the aircraft symbol or its callsign label. Tap × in the command panel to
  close the strip.
- **Heading − / +:** Assigns a controller vector in 5° steps and cancels the aircraft’s current
  waypoint route. Aircraft turn at a rate appropriate to their performance class.
- **Named waypoints:** Use **Direct** for a new route or **Append** to build a sequence of published
  fixes. **Undo waypoint** removes the last instruction and **Clear route** cancels the sequence.
- **Move and zoom the radar:** Drag the chart to pan, pinch to zoom, or use the zoom controls in the
  lower-right corner. Tapping the zoom percentage resets the view.
- **Set up approach:** For an arrival, creates progressive vectors to a stable final for its
  assigned runway. It uses a published waypoint when that produces a sensible transition and
  otherwise supplies vectors, with course changes limited to flyable angles. It also sets safe
  landing altitude and speed targets; you still decide when to clear it to land.
- **Altitude − / +:** Changes target altitude in 500 ft steps. The aircraft climbs or descends
  gradually.
- **Speed − / +:** Changes target speed in 10 kt steps. Different aircraft classes accelerate,
  slow and turn at different rates.
- **Clear to land:** Authorizes an arrival to use its assigned runway. It does not teleport or snap
  the aircraft onto final.
- **Go around:** Cancels the landing attempt, turns the aircraft onto runway heading and sends it
  toward 3,000 ft. Re-route it before trying again. Manual and automatic go-arounds reduce score.
- **Clear for take-off:** Starts a departure's take-off roll if its assigned runway is free.
- **Runway and approach assignment:** Select a valid active runway, then explicitly assign or cancel
  an approach before landing.
- **Line up and wait / cancel clearance:** Occupies the physical runway without beginning the roll;
  landing and take-off clearances remain reversible until the protected transition begins.
- **Cross runway:** A departure holding short may cross a different clear physical runway. The
  crossing is timed and appears in runway occupancy, events and replay.
- **Hold / handoff / exit clearance:** Procedural missions add simplified named-fix holds, inbound
  acceptance, outbound handoff and terminal-exit clearance controls.
- **1× / 2×:** Changes simulation speed. Commands, fuel and mission time all continue at the chosen
  rate.
- **Pause:** Stops simulation time. On supported layouts, the command panel remains available after
  resuming.

Manchester’s triangle markers use real named terminal and SID fixes such as MIRSI, ROSUN, DAYNE,
MCT and POL. Cyan fixes are controller-addressable waypoints; dimmer fixes are also sector entry or
exit boundaries. The dashed line shows the approach direction. Aircraft labels show callsign,
altitude/flight level, trend and speed; label detail can be adjusted in Settings.

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

Wake-enabled shifts show the required leader/follower spacing on flight strips. The simplified wake
rule is separate from normal 3 NM / 1,000 ft separation and has its own warning and score category.

## Weather and operational events

When the briefing says weather effects are active, wind drift changes aircraft tracks and reduced
visibility changes the accessible prediction presentation. Authored runway-direction changes give a
warning and wait for a safe occupancy window before applying. The event panel can also introduce
fictional priorities, rejected take-offs, temporary closures and prediction-assist outages. Select
**Accept recovery** when its stated recovery goal becomes active; the event feed and debrief record
the result. These are deterministic game systems, not real procedures.

## Missions and progression

- **1 — First Contact:** selection, routing and a basic arrival flow.
- **2 — Level Headed:** use 1,000 ft altitude layers where routes converge.
- **3 — Slow and Steady:** use speed to build an orderly arrival sequence.
- **4 — Cleared to Land:** judge runway availability and recover with go-arounds.
- **5 — Opening the Flow:** introduce departures and assigned exits.
- **6 — Mind the Runway:** interleave arrivals and departures without overlapping runway use.
- **7 — Mixed Picture:** account for light, jet and heavy performance differences.
- **8 — Parallel Pressure:** manage assigned traffic on both 23R and 23L.
- **9 — Heavy Spacing:** sequence simplified wake categories.
- **10 — Weather Window:** control with wind, visibility and a warned runway change.
- **11 — Hold and Handoff:** use named-fix holds and explicit procedural handoffs.
- **12 — Recovery Shift:** respond to a deterministic schedule of disruptions.
- **Harbour Approach:** a separate four-mission fictional campaign with crossing runways, compact
  fixes, procedural traffic and a squall-recovery finale.
- **Endless sector:** each pack has its own seeded stages and local high score. Endless unlocks after
  completing that pack's first authored mission.

Completing an authored mission unlocks the next one. Stars depend on score. Safe movements, short
efficient routes and prompt handling add points; conflicts, excess route distance, go-arounds and
missed exits reduce them. A low-star completion still unlocks the next mission.

## Saving, settings and recovery

Leaving or backgrounding an active shift pauses and saves a deterministic command replay. Use
**Continue** on the home screen to reconstruct it. Settings include audio levels, haptics, trails,
reduced motion, high contrast, label size, label decluttering and pause-on-focus-loss.

Custom setup can switch sector packs. A Manchester configuration uses the legacy-compatible `ATC1`
share namespace; other packs use `ATC2` with an explicit pack ID. Imports contain configuration
only—never identity, progress, analytics or save data—and use the clipboard/share sheet locally.
