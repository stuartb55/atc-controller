# Manchester Approach Feature Roadmap

This is a work-through backlog for evolving Manchester Approach without weakening its deterministic simulation, privacy-first design, accessibility, or entertainment-only positioning. Items stay unchecked until their acceptance criteria and the relevant verification gates have passed. Effort is relative (`S`, `M`, `L`, or `XL`), not a calendar commitment.

## Current state

- **Content:** Eight authored Manchester missions introduce routing, altitude, speed, landing and go-arounds, departures, runway occupancy, mixed traffic, and parallel runways. A seeded endless mode generates progressively harder finite stages.
- **Simulation:** The engine advances in fixed deterministic steps and models routes, headings, altitude, speed, fuel, runway occupancy, arrivals, departures, go-arounds, and terminal-area exits. Player commands can be replayed to reconstruct the same state.
- **Scoring:** Scenario-authored rules award safe arrivals and departures, route efficiency, time, and completion points, and apply conflict, go-around, and missed-exit penalties. Three scenario-authored thresholds determine stars.
- **Progression:** Mission stars, unlocks, tutorial completion, and the endless high score are stored locally. Completing an authored mission unlocks the next mission.
- **Save and resume:** A versioned active-session record stores a deterministic command log and checkpoint metadata. Backgrounding pauses and saves the game, and the session can be rebuilt after process death.
- **Conflict detection:** The engine predicts closest approach, reports predicted conflicts and losses of separation, issues strikes, and detects collisions and runway incursions.
- **Accessibility:** The UI supports high contrast, adjustable radar-label size, reduced motion, optional trails, haptics, and content descriptions. Compact and wide landscape layouts are supported.
- **Feedback:** Local audio, haptic cues, radar warnings, status text, and results screens communicate important actions and outcomes without network services.

## Review gaps

- Home progress, earned-star copy, runway, weather, traffic, and parts of mission briefing presentation are partly hardcoded rather than derived from the selected scenario and persisted player data.
- Authored objectives and scheduled pending traffic reach the simulation but are not presented to the player.
- Only mission one has a static slide tutorial; its copy includes an incorrect fixed callsign instead of the actual selected aircraft.
- Weather is briefing/display data and has no simulation effect.
- Label decluttering and pause-on-focus-loss are persisted but not exposed in settings; stored audio volumes are reduced to binary controls.
- Results merge time with efficiency and all penalties into a single separation line, obscuring why the score changed.
- Line-up clearance is advertised in the README but is not represented by the playable command, clearance, or aircraft-status flow.
- Aircraft are assigned to parallel runways by authored content and players cannot change those assignments.

## Product guardrails

- **Offline by design:** No accounts, ads, analytics, cloud saves, multiplayer, remote leaderboards, or network permission. Sharing uses user-initiated local Android intents only.
- **Deterministic by construction:** Simulation changes use fixed-step time, stable seeded randomness, ordered commands/events, and versioned replay formats. Visual-only animation must never affect engine outcomes.
- **Accessible without spatial precision:** Every radar action has a labelled, focusable non-spatial equivalent. Critical state is conveyed by text and shape as well as colour, with throttled announcements for assistive technology.
- **Entertainment only:** New procedures remain intentionally simplified game systems. Briefings, about text, and shared content must not imply current operational accuracy or suitability for aviation navigation or training.
- **Migration before deletion:** Persisted progress and completed replay history are versioned and migrated where compatible. Only incompatible active sessions may be discarded, with a clear explanation and without resetting unrelated progress or settings.
- **Ranked integrity:** Assists and custom configurations that change difficulty are recorded. Practice results never overwrite authored-mission or ranked challenge records.

## Phase 0 — Accurate foundations

- [x] **F-01 — Dynamic operations and career data**

  - **ID:** F-01
  - **Priority:** P0
  - **Effort:** S
  - **Dependencies:** None
  - **Player value:** Menus and briefings become trustworthy: players see their real progress and the actual conditions they are about to control.
  - **Implementation notes:** Extend mission/progress presentation models with best score and scenario-derived runway, weather, objectives, duration, and traffic summaries. Map `PlayerProgress` and the selected `ScenarioDefinition` once in the view model; do not let composables invent fallback operational values. Version the progress codec when best scores are added, preserving existing stars and unlocks. A decorative home radar preview may remain synthetic only when it is clearly labelled as such and its operational readouts still describe the selected or resumable session.
  - **Acceptance checklist:**
    - [x] Home totals, completed shifts, and earned stars are calculated from persisted progress.
    - [x] Home runway, wind/weather, traffic, and continue-state values describe their named scenario or are explicitly labelled as a non-live preview.
    - [x] Mission cards and briefings derive traffic, active runway ends, weather, duration, objectives, and tutorial focus from content.
    - [x] Each authored mission shows both its persisted best stars and best score; endless mode shows its high score without presenting mission stars.
    - [x] Existing installs retain stars, unlocks, tutorial completion, settings, and resumable sessions after the progress-format change.
    - [x] No operational value in home or briefing Compose code is a Manchester runway/weather/progress literal.

- [x] **F-02 — Complete settings and radar accessibility**

  - **ID:** F-02
  - **Priority:** P0
  - **Effort:** M
  - **Dependencies:** None
  - **Player value:** Players can tune visual density and feedback to their needs, and conflicts remain understandable without relying on colour or tiny target IDs.
  - **Implementation notes:** Expose the existing label-decluttering and pause-on-focus-loss preferences. Represent music and effects as stored 0–100% sliders with mute behavior that does not destroy the previous non-zero value. Map conflict aircraft IDs to callsigns before presentation, retain a stable conflict ordering, and add next/previous conflict actions. Add distinct predicted-conflict and separation-loss shapes/patterns to targets, connecting lines, the alert summary, and semantics. Keep controls reachable in both compact and wide layouts.
  - **Acceptance checklist:**
    - [x] Music and effects sliders load, update, mute/unmute, and persist their real stored volume values.
    - [x] Label decluttering and pause-on-focus-loss controls are visible, described, immediately effective, and persisted.
    - [x] Conflict alerts announce both callsigns and whether the pair is predicted or already below separation.
    - [x] Players can cycle every active conflict pair and focus/select either involved aircraft without using the radar canvas.
    - [x] Predicted conflicts and losses use different colour-independent shapes or patterns in normal and high-contrast modes.
    - [x] All controls work with keyboard/D-pad and TalkBack in compact and wide layouts, with touch targets and labels meeting the app's accessibility baseline.
    - [x] Rapid conflict updates are coalesced or throttled so live-region announcements remain useful.

## Phase 1 — Situational awareness and learning

- [ ] **F-03 — Live objectives, traffic horizon, and star forecast**

  - **ID:** F-03
  - **Priority:** P0
  - **Effort:** M
  - **Dependencies:** F-01
  - **Player value:** Players understand what success requires, what traffic is coming, and how close the current performance is to the next star.
  - **Implementation notes:** Add presentation models derived from `ScenarioObjectives`, score thresholds, elapsed/max duration, completed movement counts, and scheduled aircraft not yet spawned. Show a small next-traffic horizon without revealing more than the authored design intends. Calculate the next-star delta from the same total and thresholds used by the engine, including the fact that penalties can move the forecast backwards. Endless stages should use stage targets while displaying cumulative score separately.
  - **Acceptance checklist:**
    - [ ] Every authored objective has a live current/target or pass/fail presentation during play.
    - [ ] The HUD shows movements remaining and mission time remaining using engine state, including a clear overdue/failed state.
    - [ ] The next scheduled arrivals/departures show callsign or traffic type, intent, and deterministic time-to-entry; empty and completed states are handled.
    - [ ] The star forecast shows stars currently secured and points to the next threshold, and matches the final engine result at mission end.
    - [ ] Compact layouts retain critical score, objective, and next-traffic information without covering radar controls.
    - [ ] Pause, speed changes, save/resume, and replay reconstruction do not cause forecast or traffic-horizon drift.

- [ ] **F-04 — Interactive Training Academy**

  - **ID:** F-04
  - **Priority:** P0
  - **Effort:** L
  - **Dependencies:** F-01, F-03
  - **Player value:** New players learn by safely performing real actions, while returning players can replay focused lessons for any existing skill.
  - **Implementation notes:** Replace mission-one slides with authored, action-gated training steps. Bind prompts to the actual scenario, selected aircraft, callsigns, and available controls rather than static examples. Define a replayable lesson for every current `TutorialFocus`: selection/routing, altitude, speed, landing/go-around, departures, runway occupancy, mixed traffic, and parallel runways. Training state must be serializable alongside the active session; gating may pause engine time but must not alter deterministic ticks or command ordering. Rejected commands should drive contextual explanations through stable reason codes rather than raw engine strings.
  - **Acceptance checklist:**
    - [ ] Every existing `TutorialFocus` has an authored refresher that can be launched independently after initial onboarding.
    - [ ] Mission-one training uses the real selected aircraft/callsign and advances only after the requested valid action is observed.
    - [ ] Prompts point to a focusable control, have equivalent spoken instructions, and never require radar-only gestures.
    - [ ] Rejected or unsafe actions explain why and how to recover without consuming simulation time while coaching is paused.
    - [ ] Lessons are skippable, replayable, and do not change ranked scoring or unlock rules.
    - [ ] Rotation/recreation, background save, process death, and continue restore the exact lesson and deterministic simulation state.
    - [ ] Tutorial copy reiterates that procedures are simplified entertainment, not operational training.

- [ ] **F-05 — Radio and event feed**

  - **ID:** F-05
  - **Priority:** P1
  - **Effort:** M
  - **Dependencies:** F-01, F-02
  - **Player value:** Important events no longer disappear between radar updates, and players can understand and revisit what just happened without relying on sound.
  - **Implementation notes:** Promote engine `GameEvent` values into stable presentation events for spawns, clearances, command rejections, conflicts, runway incursions/occupancy, take-offs, touchdowns, landings, go-arounds, exits, and scenario outcomes. Render a brief caption plus a bounded, scrollable event log. Keep engine event types/data structured and localize only in the presentation layer. Define deterministic ordering for events sharing a tick and bound both persisted terminal history and in-memory UI history.
  - **Acceptance checklist:**
    - [ ] Every required event category produces an accurate timestamped caption and event-log entry.
    - [ ] Events use callsigns and runway/fix labels rather than internal IDs.
    - [ ] Tapping or keyboard-activating an aircraft event selects and focuses the relevant target; pair events provide access to both aircraft.
    - [ ] Command rejections include an actionable, localized reason without leaking implementation strings.
    - [ ] The log has a documented capacity and truncation rule and does not grow without bound during endless play.
    - [ ] Captions, audio, and haptics respect player settings; essential information remains available as text.
    - [ ] Event order and content are identical after deterministic reconstruction.

- [ ] **F-06 — Flight-strip board and improved routing**

  - **ID:** F-06
  - **Priority:** P1
  - **Effort:** L
  - **Dependencies:** F-02, F-05
  - **Player value:** Busy traffic becomes manageable from a concise list, and route editing becomes deliberate instead of requiring a complete redraw.
  - **Implementation notes:** Add a flight-strip board ordered by a stable urgency policy (loss/prediction, low fuel, approach/runway state, then schedule/callsign). Show callsign, phase, runway, fuel, and conflict state. Extend routing commands to support Direct to any named fix, clear route, undo last waypoint, and append route. Optional snap-to-fix and snap-to-final are presentation assists that must produce explicit normalized waypoints before submission so replay remains deterministic. Preserve the current full-route command for compatibility and codec migration.
  - **Acceptance checklist:**
    - [ ] All active aircraft appear once in a stable, documented urgency order with callsign, phase, runway, fuel, and conflict status.
    - [ ] Selecting a strip selects the radar target and vice versa without unexpected list reordering stealing focus.
    - [ ] Direct supports every valid named fix and rejects invalid arrival/departure choices with a useful explanation.
    - [ ] Clear, undo, append, and replace-route actions have predictable results and serialize into replay commands.
    - [ ] Snap-to-fix/final can be disabled and never changes an already committed route silently.
    - [ ] Every routing operation is available through labelled keyboard/TalkBack controls as well as touch drawing.
    - [ ] Route validation, save/resume, and replay tests cover empty, maximum-length, malformed, and out-of-bounds routes.

- [ ] **F-07 — Detailed debrief and deterministic replay**

  - **ID:** F-07
  - **Priority:** P1
  - **Effort:** XL
  - **Dependencies:** F-01, F-03, F-05
  - **Player value:** Players can see exactly where a result came from, learn from each flight, and review a completed shift without risking their continue slot.
  - **Implementation notes:** Expand score/result models to keep base movement points, time, route efficiency, completion, avoidable/manual go-arounds, missed exits, separation, and future penalty categories distinct. Record per-aircraft outcomes and an ordered terminal event timeline. Build deterministic playback from the scenario identity/version, stable seed, commands, and terminal tick with play/pause, step, speed, scrub-to-checkpoint, and target-follow controls. Generate route heatmaps from replayed engine positions, not sampled UI animation. Store bounded completed-replay history separately from `ActiveSessionRecord`; opening or deleting a replay must not affect continue state.
  - **Acceptance checklist:**
    - [ ] The debrief displays every scoring category separately and the signed rows sum exactly to the final score.
    - [ ] Each flight shows its outcome, route efficiency, elapsed handling time, penalties, and associated timeline events.
    - [ ] “Cost of next star” uses the achieved score and authored thresholds, including a clear maximum-star state.
    - [ ] The event timeline and route heatmap use deterministic engine data and identify relevant callsigns/runways.
    - [ ] Replay controls support play/pause, step, speed, seek/scrub, and aircraft follow without submitting new gameplay commands.
    - [ ] Replaying from start or any stored checkpoint reaches the original terminal tick, state hash, event order, score, and result at every supported speed.
    - [ ] Completed replay history is bounded, versioned, and isolated from the single resumable active-session record.
    - [ ] Incompatible replays fail gracefully with retained career progress and a clear local-only error state.

## Phase 2 — Offline replayability

- [ ] **F-08 — Controller service record and mastery**

  - **ID:** F-08
  - **Priority:** P1
  - **Effort:** L
  - **Dependencies:** F-01, F-07
  - **Player value:** Long-term progress reflects how players control traffic, not just how many missions they have unlocked.
  - **Implementation notes:** Add a versioned local service record for total safe movements, best score and best completion time per authored mission, safe streaks, per-`TutorialFocus` mastery, and achievements. Derive record updates from validated terminal results so replayed, abandoned, practice, or duplicate results cannot increment counters accidentally. Define achievement predicates in data with stable IDs, including zero strikes, perfect exits, and efficient routing. Recommendations should use deterministic local rules and always explain the selected mission or weak skill.
  - **Acceptance checklist:**
    - [ ] Total movements and safe streaks update once per eligible completed result and survive process death.
    - [ ] Best score and best completion time are retained independently for each authored mission.
    - [ ] Mastery has a documented calculation for every existing tutorial focus and exposes the contributing results.
    - [ ] Zero-strike, perfect-exit, and efficient-routing achievements have explicit thresholds and unlock deterministically.
    - [ ] The next recommendation identifies either the next unlocked mission or a weak skill and gives a player-readable reason.
    - [ ] Practice, imported, replayed, malformed, and failed results cannot corrupt ranked records or double-count progress.
    - [ ] Existing progress migrates without fabricating unavailable historical statistics.

- [ ] **F-09 — Practice and custom seeded shifts**

  - **ID:** F-09
  - **Priority:** P1
  - **Effort:** L
  - **Dependencies:** F-01, F-03, F-08
  - **Player value:** Players can rehearse a difficult skill or build a preferred traffic challenge without losing the fairness of authored records.
  - **Implementation notes:** Define a validated `ShiftConfiguration` covering density, traffic mix, runway direction, weather preset, fuel pressure, strike limit, and assists. Separate authored/ranked and practice result namespaces in presentation and persistence. Encode every outcome-affecting option plus a stable generator version into deterministic ranked configurations; reject or normalize impossible combinations before starting the engine. Assists must be visible in the HUD and debrief.
  - **Acceptance checklist:**
    - [ ] Players can configure density, arrival/departure mix, runway direction, weather, fuel, strikes, seed, and supported assists entirely offline.
    - [ ] The setup screen validates combinations and previews traffic/objective implications before start.
    - [ ] The same generator version, configuration, and seed produce identical scenarios and terminal replays on supported devices.
    - [ ] Practice is clearly marked before, during, and after play and cannot overwrite mission stars, bests, achievements, daily streaks, or endless high score.
    - [ ] Ranked presets lock or encode every outcome-affecting option and display their configuration identity.
    - [ ] Custom shifts save/resume exactly, including seed, assists, pending traffic, and generator version.
    - [ ] Configuration decoding rejects malformed or unsupported values without starting a partial session.

- [ ] **F-10 — Offline Daily Shift and share codes**

  - **ID:** F-10
  - **Priority:** P1
  - **Effort:** M
  - **Dependencies:** F-09
  - **Player value:** A fresh, comparable challenge and portable custom shifts add replayability without accounts, servers, or tracking.
  - **Implementation notes:** Derive the daily configuration from the device-local ISO date, an explicit challenge epoch, and a stable generator version. Store results and streaks against the date/configuration identity, and define behavior for timezone/date changes without claiming global synchronization. Create a checksummed, size-bounded share-code format containing only generator version, seed, configuration, and optional display metadata—never player identity, history, or save state. Import/export through user-initiated Android share sheet and clipboard actions.
  - **Acceptance checklist:**
    - [ ] A given local date and generator version always produce the same Daily Shift configuration offline.
    - [ ] The UI states that Daily Shifts use the device's local date and are not a synchronized global leaderboard.
    - [ ] One eligible result per date contributes to the local daily record/streak under a documented retry policy.
    - [ ] Forward/backward clock or timezone changes cannot silently duplicate rewards; affected dates remain explainable in local history.
    - [ ] Share-code round trips preserve seed and every outcome-affecting configuration field with a version and checksum.
    - [ ] Malformed, oversized, unknown-version, and semantically invalid codes are rejected safely before session creation.
    - [ ] Import/export uses Android sharing or clipboard only, includes no personal data, and adds no network permission or background communication.

- [ ] **F-11 — Endless-stage milestones**

  - **ID:** F-11
  - **Priority:** P1
  - **Effort:** M
  - **Dependencies:** F-01, F-07
  - **Player value:** Endless mode gains meaningful decision points and feedback while retaining the tension of a continuous high-score run.
  - **Implementation notes:** Replace automatic stage rollover with a deterministic milestone state after a finite stage completes. Present the stage breakdown, next generator inputs, personal-best delta, and cash-out/continue choice. Store the completed stage, cumulative score, next-stage seed/configuration, and choice state in the resumable session. Cash-out finalizes the run exactly once; continue creates the next stage without resetting cumulative scoring or corrupting replay boundaries.
  - **Acceptance checklist:**
    - [ ] Every endless stage stops before the next begins and shows a concise stage debrief and next-stage preview.
    - [ ] The milestone displays cumulative score and the delta to the saved endless personal best.
    - [ ] Cash out records the final high score once and removes the resumable run only after persistence succeeds.
    - [ ] Continue uses the previewed stage number, seed, traffic level, objectives, and cumulative score.
    - [ ] Backgrounding or process death before a choice restores the exact milestone; after a choice it restores the correct finalized or next-stage state.
    - [ ] Stage and full-run replays preserve their boundaries, cumulative score, event order, and deterministic outcome.
    - [ ] Failure during a stage reports the full run total without offering an invalid continue choice.

## Phase 3 — Operational depth

- [ ] **F-12 — Runway assignment and clearance ladder**

  - **ID:** F-12
  - **Priority:** P2
  - **Effort:** XL
  - **Dependencies:** F-04, F-05, F-06, F-07
  - **Player value:** Parallel-runway scenarios become genuine control problems, with reversible clearances and runway occupancy decisions instead of fixed authored assignments.
  - **Implementation notes:** Extend engine commands, clearances, statuses, validation reasons, events, replay codec, and UI for runway assignment, approach assignment, line-up-and-wait, landing/take-off cancellation, and runway crossing/occupancy. Model physical runway occupancy separately from directional runway-end assignment and define reciprocal/parallel conflicts. All state transitions must be explicit and deterministic. Update the README only when line-up is playable. Ship authored academy steps before exposing mechanics in ranked missions.
  - **Acceptance checklist:**
    - [ ] Eligible aircraft can be assigned among valid active runway ends, and invalid changes are rejected with a recoverable reason.
    - [ ] Players can assign/cancel an approach, issue/cancel landing or take-off clearance, and issue line-up-and-wait through spatial and non-spatial controls.
    - [ ] Line-up changes aircraft state and physical runway occupancy without starting a take-off roll.
    - [ ] Crossing, reciprocal-end, arrival/departure, and multiple-occupant rules produce deterministic warnings, rejections, go-arounds, or incursions as authored.
    - [ ] Events, flight strips, HUD, captions, debriefs, and replay distinguish assignment, line-up, cancellation, crossing, and occupancy states.
    - [ ] Existing commands and save data migrate where compatible; unsupported active sessions are discarded without losing settings/progress.
    - [ ] Authored training covers the clearance ladder and parallel-runway reassignment before the first ranked scenario that requires them.

- [ ] **F-13 — Wake-turbulence separation**

  - **ID:** F-13
  - **Priority:** P2
  - **Effort:** L
  - **Dependencies:** F-04, F-07, F-12
  - **Player value:** Aircraft performance classes create visible sequencing trade-offs and reward planning beyond generic distance separation.
  - **Implementation notes:** Define a simplified, authored leader/follower minima table for light, medium/regional, jet, and heavy classes in the entertainment model. Evaluate wake separation for relevant runway/final/departure sequences using fixed-step engine state, with structured predicted and actual violation events. Keep wake scoring separate from generic separation and collision logic. Visualize the required spacing with text plus distinct shapes/patterns, and add a focused mission and academy lesson.
  - **Acceptance checklist:**
    - [ ] Every supported leader/follower class pairing has a validated, documented deterministic minimum.
    - [ ] The engine identifies leader, follower, operation/runway context, required minimum, and actual separation.
    - [ ] Predicted wake warnings and actual violations are visually and semantically distinct from ordinary conflict alerts.
    - [ ] Wake penalties/strikes are applied once under documented recovery/reset rules and appear in their own result category.
    - [ ] Runway reassignment, go-around, take-off cancellation, and crossing transitions update wake sequencing correctly.
    - [ ] A dedicated authored mission and replayable academy lesson teach the simplified mechanic and entertainment-only caveat.
    - [ ] Fixed-step, multiple-speed, save/rebuild, and replay tests produce identical wake events and scores.

- [ ] **F-14 — Gameplay-relevant weather**

  - **ID:** F-14
  - **Priority:** P2
  - **Effort:** XL
  - **Dependencies:** F-07, F-09, F-12
  - **Player value:** Wind and visibility become planning constraints, making runway choice and vectoring meaningfully different between shifts.
  - **Implementation notes:** Expand weather content with deterministic wind vectors, gust policy if used, visibility, and effective change events. Apply wind drift in the fixed-step engine, define simplified aircraft/runway crosswind limits, and make reduced visibility affect presentation through explicit, accessible rules rather than random concealment. Runway-direction changes must be authored deterministic events with warning and transition windows. Include weather version/state in configuration, save, result, and replay data.
  - **Acceptance checklist:**
    - [ ] Briefing, HUD, and debrief show the same wind, visibility, crosswind implications, and scheduled changes used by the engine.
    - [ ] Wind drift is fixed-step deterministic and produces identical positions at every supported simulation speed and after reconstruction.
    - [ ] Crosswind limits are simplified, class-aware, validated, and result in clear assignment/clearance feedback.
    - [ ] Reduced visibility has a documented accessible effect with equivalent non-visual status; it does not hide safety-critical information only from sighted users.
    - [ ] Deterministic runway-direction changes provide advance warning, a safe recovery path for active approaches/departures, and explicit scoring rules.
    - [ ] Weather can affect objectives and scoring through authored rules, with separate debrief attribution.
    - [ ] Custom configurations, active saves, completed replays, and shared codes preserve the full weather version/state.

- [ ] **F-15 — Holding and procedural control**

  - **ID:** F-15
  - **Priority:** P2
  - **Effort:** XL
  - **Dependencies:** F-04, F-05, F-06, F-12
  - **Player value:** Players gain tools to absorb delay and manage complete arrival/departure flows, creating deeper decisions around fuel, sequencing, and handoff timing.
  - **Implementation notes:** Add fix-based hold assignment/cancellation with deterministic leg timing, explicit approach clearances, authored missed-approach routes, inbound/outbound handoffs, and terminal exit clearances. Keep procedures deliberately simplified and content-defined. Extend route semantics so holds and procedural routes cannot be confused with free-drawn waypoints. Fuel, command validation, events, objectives, and categorized scoring must account for delayed or incorrect control actions.
  - **Acceptance checklist:**
    - [ ] Players can assign and cancel a valid fix-based hold with direction/level as defined by simplified content rules.
    - [ ] Hold geometry and leg timing are deterministic across speeds, pause, save/rebuild, and replay.
    - [ ] Approach clearance transitions to an authored procedure, and go-around follows the correct missed-approach route.
    - [ ] Handoffs and exit clearances have explicit eligibility, acknowledgement, completion, rejection, and timeout states.
    - [ ] Fuel continues to matter in holds and procedural delays, with advance warnings and recoverable priority decisions.
    - [ ] Events, strips, objectives, and debrief categories explain hold time, procedural compliance, handoffs, exits, and related score effects.
    - [ ] Academy lessons and non-spatial controls cover every new procedure before ranked use.

- [ ] **F-16 — Emergencies and dynamic events**

  - **ID:** F-16
  - **Priority:** P2
  - **Effort:** XL
  - **Dependencies:** F-04, F-05, F-07, F-12
  - **Player value:** Rare, understandable disruptions create memorable recovery decisions without turning outcomes into hidden randomness.
  - **Implementation notes:** Build an authored deterministic event schedule/state machine for low-fuel priority traffic, rejected take-offs, temporary runway closures, simplified equipment outages, and priority flights. Each event definition must include trigger, warning lead time, affected entities, allowed responses, recovery/end condition, and scoring/objective policy. Seeds may choose among authored events only through the stable generator. Store event state and version in checkpoints/replays; never source real-world incidents or live aviation data.
  - **Acceptance checklist:**
    - [ ] Every event type has a deterministic trigger and complete warning, active, recovery, and resolved/failed lifecycle.
    - [ ] Players receive text/shape alerts, a recommended recovery goal, and accessible controls with enough authored time to respond.
    - [ ] Low-fuel priority, rejected take-off, closure, outage, and priority-flight states interact safely with runways, clearances, conflicts, and fuel.
    - [ ] Each event has explicit objective and scoring rules shown in the briefing/event feed/debrief.
    - [ ] Event selection and outcomes reproduce across speed changes, save/rebuild, checkpoint seek, and full replay.
    - [ ] Authored introductions are replayable and clearly describe the mechanics as fictionalized entertainment.
    - [ ] Content validation rejects events with missing warnings, no recovery path, invalid references, or impossible timing.

## Phase 4 — Content expansion

- [ ] **F-17 — Airport registry and second sector pack**

  - **ID:** F-17
  - **Priority:** P3
  - **Effort:** XL
  - **Dependencies:** F-01, F-07, F-09, F-12, F-13, F-14, F-15, F-16
  - **Player value:** A contrasting airport and campaign provide substantial new replayability while keeping Manchester progress intact.
  - **Implementation notes:** Introduce an airport/content registry keyed by stable airport, campaign, scenario, fix, and runway IDs before adding the second pack. Remove direct `ManchesterContent` lookups and Manchester-specific UI/persistence assumptions from adapters, view models, generators, localization, saves, replays, service records, and share codes. Namespace persisted content identities and validate cross-references. The second airport must use a deliberately simplified, attributed layout and offer a contrasting operational shape without implying current real-world accuracy.
  - **Acceptance checklist:**
    - [ ] All existing Manchester missions, endless stages, practice configurations, saves, and replays resolve through the registry with unchanged deterministic outcomes.
    - [ ] Airport-specific runways, fixes, map bounds, weather, strings, source metadata, and disclaimers come from the selected content pack.
    - [ ] Persistence and share codes namespace stable content/version IDs and migrate legacy Manchester identities without resetting progress.
    - [ ] Content validation catches duplicate IDs, missing airport/scenario/fix/runway references, invalid procedures, and unsupported mechanic versions across packs.
    - [ ] The second pack includes a complete campaign, academy introductions for its differences, endless/practice support, attribution, and entertainment-only notices.
    - [ ] Switching packs never mixes progress, objectives, traffic, runways, events, or service-record results between airports.
    - [ ] Both packs work fully in airplane mode on API 26+, in compact/wide layouts, and through save/resume and deterministic replay.

## Interface and data evolution

The roadmap is expected to extend these internal contracts without creating a network-facing API:

- **Presentation state:** Add objective progress, traffic forecasts, star forecast, event captions/history, flight strips, training state, milestone state, assists, weather effects, service records, and categorized results to immutable UI models. Keep localization and accessibility descriptions in the presentation layer.
- **Content:** Extend scenario definitions for richer weather, procedures, training steps, event schedules, ranked configuration identity, and mechanic/content versions. Add a registry before introducing a second airport.
- **Engine:** Extend commands, clearances, aircraft/runway statuses, events, objectives, and scoring categories. New randomness must come only from stable seeded generators; engine advancement remains fixed-step and independent of rendering or wall-clock time.
- **Persistence:** Version player progress, service records, shift configurations, active sessions, and completed replay history independently. Migrate compatible data, preserve unknown historical absence honestly, and discard only an incompatible active session when reconstruction cannot be made safe.
- **Replay:** Record scenario/content/generator versions, seed/configuration, ordered player commands, deterministic event state, terminal tick, and verification hash. Completed history remains separate from the one resumable-session slot and is subject to a documented local storage bound.
- **Sharing:** Share codes and Android intents contain configuration data only. They never include player identity, career history, analytics, or an active-session payload, and require no network permission.

## Verification strategy

### Engine and determinism

- Add focused JVM tests for every new command, status transition, clearance, event, objective, and scoring rule.
- Run each mechanic at every supported simulation speed and compare terminal snapshot, event ordering, score, and deterministic hash.
- Reconstruct from start and supported checkpoints, including pause/resume, process-death payloads, and replay seeks.
- Test boundary combinations: reciprocal/parallel runways, events on the same tick, zero/low fuel, visibility/crosswind limits, terminal ticks, and maximum route/event-log sizes.

### Persistence and content

- Add migration tests from every retained schema fixture plus malformed, truncated, oversized, unknown-version, and semantically invalid data.
- Prove incompatible active sessions can be cleared without losing settings, progress, service records, or completed compatible replays.
- Add stable challenge-seed/share-code golden tests across dates, timezones, generator versions, and import/export round trips.
- Extend content validation for objectives, thresholds, runway/fix/procedure references, tutorials, event lifecycles, mechanic versions, and registry uniqueness.

### UI and accessibility

- Add Compose tests for navigation, objective/forecast updates, event selection, training gates, debrief/replay controls, settings persistence, and endless milestones.
- Exercise compact and wide layouts, font/label scaling, keyboard/D-pad traversal, TalkBack semantics, high contrast, reduced motion, and colour-independent alerts.
- Verify live regions are throttled/coalesced, focus remains stable as urgency lists update, and every spatial radar operation has a non-spatial path.

### Device smoke tests

- Verify process death, app backgrounding, focus-loss preference, checkpoint failures, continue, tutorial restore, and completed-replay isolation.
- Verify mission unlocks, best records, reset-progress behavior, endless transitions, Daily Shift date changes, and practice/ranked separation.
- Run in airplane mode and confirm there is no network permission, attempted network traffic, or feature degradation.
- Cover the minimum supported Android 8.0/API 26 device and a current target device, including low-memory recreation.

## Definition of done

A backlog item is complete only when all of its acceptance boxes are checked, its data/content migrations are documented, and relevant automated and device tests pass. A phase is complete only after all items in that phase are complete and a fresh JDK 21 environment—not cached reports—passes:

```text
./gradlew test
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
```

Release notes and player-facing copy must preserve the offline/privacy promise and the entertainment-only aviation disclaimer. Feature implementation is intentionally separate from this roadmap document.
