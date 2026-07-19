# ATC Controller Agent Guide

## Product goal

Build an approachable Android air-traffic-control simulation in which players issue clear commands to aircraft, understand the consequences, and can reliably complete missions.

## Priorities

1. Mission correctness: objectives must track real game events and complete reliably.
2. Command usability: selecting an aircraft and issuing heading, altitude, speed, approach, landing, and related commands must be fast and understandable.
3. Situational awareness: aircraft state, runway/airport context, objectives, warnings, and feedback must be legible at a glance.
4. Progressive learning: the first mission must teach the interaction model without overwhelming the player.
5. Verification: preserve or add automated tests and exercise important flows on an Android emulator.

## Working agreements

- Inspect existing architecture and conventions before changing implementation patterns.
- Keep simulation/domain logic separate from Compose UI wherever practical.
- Prefer deterministic state transitions and testable mission conditions over timing-dependent UI behavior.
- Treat mission completion, timeout, crash, command acknowledgement, and unsafe-state feedback as explicit user-visible states.
- Design for common Android phone sizes, touch targets of at least 48 dp, readable contrast, and accessibility semantics.
- Avoid hidden gestures for essential controls; primary actions must be discoverable.
- Keep radar interaction available while commands or mission guidance are shown.
- Add or update tests for every mission-state bug and important interaction-state change.
- Run the narrowest relevant tests during development, then the broader test suite before handoff.
- Do not discard unrelated user changes in the worktree.

## Collaboration boundaries

- Agents may inspect the whole repository, but should edit only files clearly belonging to their assigned subtask unless coordinating first.
- Report touched files, behavior changes, tests run, and any remaining risks.
- If two tasks need the same file, coordinate ownership before editing to avoid overwriting shared work.

## Definition of done

- The first mission can be completed through the intended aircraft-command flow and does not time out after its completion conditions are met.
- Mission objectives and outcomes are clearly visible and accurately reflect simulation state.
- The application has a coherent, user-friendly navigation and gameplay UI across its main screens.
- Aircraft selection and command issuance provide immediate, clear feedback.
- Relevant automated tests pass, the app builds, and the critical flow is exercised on an Android virtual device.
