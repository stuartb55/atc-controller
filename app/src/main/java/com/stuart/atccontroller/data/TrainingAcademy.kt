package com.stuart.atccontroller.data

/** Stable, localization-free action gates used by deterministic Academy lessons. */
enum class TrainingAction {
    SELECT_AIRCRAFT,
    SET_ROUTE,
    SET_ALTITUDE,
    SET_SPEED,
    PREPARE_APPROACH,
    CLEAR_TO_LAND,
    GO_AROUND,
    LINE_UP_AND_WAIT,
    CLEAR_FOR_TAKEOFF,
    ASSIGN_RUNWAY,
    ASSIGN_APPROACH,
    ASSIGN_HOLD,
    CANCEL_HOLD,
    ACKNOWLEDGE_HANDOFF,
}

enum class TrainingTargetOperation { ARRIVAL, DEPARTURE }

data class TrainingStepDefinition(
    val id: String,
    val action: TrainingAction,
    val targetOperation: TrainingTargetOperation? = null,
)

data class TrainingLessonDefinition(
    val id: String,
    val focus: TutorialFocus,
    val steps: List<TrainingStepDefinition>,
) {
    init {
        require(id.isNotBlank() && steps.isNotEmpty())
        require(steps.map(TrainingStepDefinition::id).distinct().size == steps.size)
    }
}

object TrainingAcademy {
    const val SELECTION_AND_ROUTING_LESSON_ID = "selection_and_routing_v2"

    private val legacySelectionAndRoutingLessonIds = setOf(
        "selection_and_routing",
        TutorialFocus.SELECTION_AND_ROUTING.name,
    )

    val lessons: List<TrainingLessonDefinition> = listOf(
        lesson(
            SELECTION_AND_ROUTING_LESSON_ID,
            TutorialFocus.SELECTION_AND_ROUTING,
            step("select_arrival", TrainingAction.SELECT_AIRCRAFT, TrainingTargetOperation.ARRIVAL),
            step("prepare_approach", TrainingAction.PREPARE_APPROACH, TrainingTargetOperation.ARRIVAL),
            step("clear_land", TrainingAction.CLEAR_TO_LAND, TrainingTargetOperation.ARRIVAL),
        ),
        lesson(
            TutorialFocus.ALTITUDE,
            step("select_arrival", TrainingAction.SELECT_AIRCRAFT, TrainingTargetOperation.ARRIVAL),
            step("set_altitude", TrainingAction.SET_ALTITUDE, TrainingTargetOperation.ARRIVAL),
        ),
        lesson(
            TutorialFocus.SPEED,
            step("select_arrival", TrainingAction.SELECT_AIRCRAFT, TrainingTargetOperation.ARRIVAL),
            step("set_speed", TrainingAction.SET_SPEED, TrainingTargetOperation.ARRIVAL),
        ),
        lesson(
            TutorialFocus.LANDING_AND_GO_AROUND,
            step("select_arrival", TrainingAction.SELECT_AIRCRAFT, TrainingTargetOperation.ARRIVAL),
            step("prepare_approach", TrainingAction.PREPARE_APPROACH, TrainingTargetOperation.ARRIVAL),
            step("clear_land", TrainingAction.CLEAR_TO_LAND, TrainingTargetOperation.ARRIVAL),
            step("go_around", TrainingAction.GO_AROUND, TrainingTargetOperation.ARRIVAL),
        ),
        lesson(
            TutorialFocus.DEPARTURES,
            step("select_departure", TrainingAction.SELECT_AIRCRAFT, TrainingTargetOperation.DEPARTURE),
            step("route_departure", TrainingAction.SET_ROUTE, TrainingTargetOperation.DEPARTURE),
            step("line_up", TrainingAction.LINE_UP_AND_WAIT, TrainingTargetOperation.DEPARTURE),
            step("clear_takeoff", TrainingAction.CLEAR_FOR_TAKEOFF, TrainingTargetOperation.DEPARTURE),
        ),
        lesson(
            TutorialFocus.RUNWAY_OCCUPANCY,
            step("select_departure", TrainingAction.SELECT_AIRCRAFT, TrainingTargetOperation.DEPARTURE),
            step("line_up", TrainingAction.LINE_UP_AND_WAIT, TrainingTargetOperation.DEPARTURE),
            step("clear_takeoff", TrainingAction.CLEAR_FOR_TAKEOFF, TrainingTargetOperation.DEPARTURE),
        ),
        lesson(
            TutorialFocus.MIXED_TRAFFIC,
            step("select_aircraft", TrainingAction.SELECT_AIRCRAFT),
            step("set_route", TrainingAction.SET_ROUTE),
            step("set_altitude", TrainingAction.SET_ALTITUDE),
            step("set_speed", TrainingAction.SET_SPEED),
        ),
        lesson(
            TutorialFocus.PARALLEL_RUNWAYS,
            step("select_arrival", TrainingAction.SELECT_AIRCRAFT, TrainingTargetOperation.ARRIVAL),
            step("assign_runway", TrainingAction.ASSIGN_RUNWAY, TrainingTargetOperation.ARRIVAL),
            step("assign_approach", TrainingAction.ASSIGN_APPROACH, TrainingTargetOperation.ARRIVAL),
        ),
        lesson(
            TutorialFocus.WAKE_TURBULENCE,
            step("select_departure", TrainingAction.SELECT_AIRCRAFT, TrainingTargetOperation.DEPARTURE),
            step("line_up", TrainingAction.LINE_UP_AND_WAIT, TrainingTargetOperation.DEPARTURE),
            step("clear_takeoff", TrainingAction.CLEAR_FOR_TAKEOFF, TrainingTargetOperation.DEPARTURE),
        ),
        lesson(
            TutorialFocus.WEATHER_OPERATIONS,
            step("select_arrival", TrainingAction.SELECT_AIRCRAFT, TrainingTargetOperation.ARRIVAL),
            step("assign_runway", TrainingAction.ASSIGN_RUNWAY, TrainingTargetOperation.ARRIVAL),
            step("assign_approach", TrainingAction.ASSIGN_APPROACH, TrainingTargetOperation.ARRIVAL),
        ),
        lesson(
            TutorialFocus.PROCEDURAL_CONTROL,
            step("select_arrival", TrainingAction.SELECT_AIRCRAFT, TrainingTargetOperation.ARRIVAL),
            step("ack_handoff", TrainingAction.ACKNOWLEDGE_HANDOFF, TrainingTargetOperation.ARRIVAL),
            step("assign_hold", TrainingAction.ASSIGN_HOLD, TrainingTargetOperation.ARRIVAL),
            step("cancel_hold", TrainingAction.CANCEL_HOLD, TrainingTargetOperation.ARRIVAL),
            step("assign_approach", TrainingAction.ASSIGN_APPROACH, TrainingTargetOperation.ARRIVAL),
        ),
        lesson(
            TutorialFocus.DYNAMIC_EVENTS,
            step("select_aircraft", TrainingAction.SELECT_AIRCRAFT),
            step("set_route", TrainingAction.SET_ROUTE),
        ),
    )

    fun lessonFor(focus: TutorialFocus): TrainingLessonDefinition? =
        lessons.firstOrNull { it.focus == focus }

    /**
     * Restores a persisted lesson cursor without allowing an older lesson layout to skip a newly
     * required action. Version one stored raw list indices for select, route, altitude, and land.
     * Every in-progress legacy step after selection resumes at the combined approach setup step.
     */
    fun restoredStepIndex(
        focus: TutorialFocus,
        persistedLessonId: String?,
        persistedStepIndex: Int,
    ): Int? {
        val lesson = lessonFor(focus) ?: return null
        if (persistedLessonId == lesson.id) {
            return persistedStepIndex.takeIf { it in lesson.steps.indices }
        }
        if (focus == TutorialFocus.SELECTION_AND_ROUTING &&
            persistedLessonId in legacySelectionAndRoutingLessonIds
        ) {
            return when (persistedStepIndex) {
                0 -> 0
                in 1..3 -> 1
                else -> null
            }
        }
        return persistedStepIndex.takeIf {
            persistedLessonId == focus.name && it in lesson.steps.indices
        }
    }

    private fun lesson(
        focus: TutorialFocus,
        vararg steps: TrainingStepDefinition,
    ) = lesson(focus.name.lowercase(), focus, *steps)

    private fun lesson(
        id: String,
        focus: TutorialFocus,
        vararg steps: TrainingStepDefinition,
    ) = TrainingLessonDefinition(
        id = id,
        focus = focus,
        steps = steps.toList(),
    )

    private fun step(
        id: String,
        action: TrainingAction,
        targetOperation: TrainingTargetOperation? = null,
    ) = TrainingStepDefinition(id, action, targetOperation)
}
