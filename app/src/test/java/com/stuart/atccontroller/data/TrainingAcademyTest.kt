package com.stuart.atccontroller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
