package com.stuart.atccontroller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingAcademyTest {
    @Test
    fun everyPlayableTutorialFocusHasOneAuthoredActionGatedLesson() {
        val playableFocuses = TutorialFocus.entries.filterNot { it == TutorialFocus.NONE }.toSet()

        assertEquals(playableFocuses, TrainingAcademy.lessons.map { it.focus }.toSet())
        assertEquals(playableFocuses.size, TrainingAcademy.lessons.size)
        TrainingAcademy.lessons.forEach { lesson ->
            assertNotNull(TrainingAcademy.lessonFor(lesson.focus))
            assertTrue(lesson.steps.isNotEmpty())
            assertEquals(lesson.steps.size, lesson.steps.map { it.id }.distinct().size)
        }
    }

    @Test
    fun firstLessonTeachesTheCompleteFlyableArrivalFlow() {
        val lesson = checkNotNull(
            TrainingAcademy.lessonFor(TutorialFocus.SELECTION_AND_ROUTING),
        )

        assertEquals(TrainingAcademy.SELECTION_AND_ROUTING_LESSON_ID, lesson.id)
        assertEquals(
            listOf(
                TrainingAction.SELECT_AIRCRAFT,
                TrainingAction.PREPARE_APPROACH,
                TrainingAction.CLEAR_TO_LAND,
            ),
            lesson.steps.map(TrainingStepDefinition::action),
        )
    }

    @Test
    fun legacyFirstLessonStepsResumeWithoutSkippingApproachSetup() {
        assertEquals(
            0,
            TrainingAcademy.restoredStepIndex(
                TutorialFocus.SELECTION_AND_ROUTING,
                "selection_and_routing",
                0,
            ),
        )
        (1..3).forEach { legacyStep ->
            assertEquals(
                1,
                TrainingAcademy.restoredStepIndex(
                    TutorialFocus.SELECTION_AND_ROUTING,
                    TutorialFocus.SELECTION_AND_ROUTING.name,
                    legacyStep,
                ),
            )
        }
        assertEquals(
            2,
            TrainingAcademy.restoredStepIndex(
                TutorialFocus.SELECTION_AND_ROUTING,
                TrainingAcademy.SELECTION_AND_ROUTING_LESSON_ID,
                2,
            ),
        )
        assertNull(
            TrainingAcademy.restoredStepIndex(
                TutorialFocus.SELECTION_AND_ROUTING,
                "selection_and_routing",
                4,
            ),
        )
    }
}
