package com.stuart.atccontroller

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun simulationAndSessionOwnershipRemainAndroidAndComposeFree() {
        val sourceRoots = listOf(
            File("src/main/java/com/stuart/atccontroller/simulation"),
            File("src/main/java/com/stuart/atccontroller/ui/session"),
        )
        val sources = sourceRoots
            .filter(File::isDirectory)
            .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }

        assertTrue("Expected production Kotlin sources in the architecture boundary", sources.isNotEmpty())
        val violations = sources.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                val forbidden = FORBIDDEN_IMPORTS.firstOrNull(line::startsWith) ?: return@mapIndexedNotNull null
                "${source.invariantSeparatorsPath}:${index + 1} imports $forbidden"
            }
        }

        assertTrue(
            "Deterministic simulation/session ownership must stay Android-free:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private companion object {
        val FORBIDDEN_IMPORTS = listOf(
            "import android.",
            "import androidx.compose.",
        )
    }
}
