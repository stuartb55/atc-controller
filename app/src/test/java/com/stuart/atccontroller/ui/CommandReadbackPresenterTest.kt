package com.stuart.atccontroller.ui

import com.stuart.atccontroller.R
import com.stuart.atccontroller.simulation.PlayerCommand
import com.stuart.atccontroller.simulation.Vec2
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandReadbackPresenterTest {
    private val strings = StringResolver { id, args ->
        when (id) {
            R.string.command_heading -> "heading ${args[0].toString().padStart(3, '0')}"
            R.string.command_altitude -> "altitude ${args[0]} feet"
            R.string.command_direct -> "direct ${args[0]}"
            else -> "resource-$id"
        }
    }
    private val presenter = CommandReadbackPresenter(strings) { point ->
        if (point == Vec2(.2, .4)) "DAYNE" else null
    }

    @Test
    fun numericAndNamedCommandsProduceExplicitControllerReadbacks() {
        assertEquals(
            "heading 355",
            presenter.commandLabel(PlayerCommand.SetTargetHeading("a1", -5.0)),
        )
        assertEquals(
            "altitude 7000 feet",
            presenter.commandLabel(PlayerCommand.SetTargetAltitude("a1", 7_000.0)),
        )
        assertEquals(
            "direct DAYNE",
            presenter.commandLabel(PlayerCommand.DirectTo("a1", Vec2(.2, .4))),
        )
    }

    @Test
    fun readbackStateRetainsTheCallsignAcrossAcceptanceAndRejection() {
        val submitted = presenter.submitted(
            sequence = 7,
            aircraftId = "a1",
            callsign = "NORTH 201",
            command = PlayerCommand.SetTargetHeading("a1", 90.0),
        )

        assertEquals(CommandReadbackStatus.SUBMITTED, submitted.status)
        assertEquals(CommandReadbackStatus.ACCEPTED, presenter.accepted(submitted).status)
        assertEquals(
            "Runway occupied",
            presenter.rejected(submitted, "Runway occupied").detail,
        )
        assertEquals("NORTH 201", submitted.callsign)
    }
}
