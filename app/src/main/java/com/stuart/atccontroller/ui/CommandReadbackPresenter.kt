package com.stuart.atccontroller.ui

import com.stuart.atccontroller.R
import com.stuart.atccontroller.simulation.PlayerCommand
import com.stuart.atccontroller.simulation.Vec2
import kotlin.math.roundToInt

internal class CommandReadbackPresenter(
    private val strings: StringResolver,
    private val fixName: (Vec2) -> String? = { null },
) {
    fun commandLabel(command: PlayerCommand): String = when (command) {
        is PlayerCommand.SetTargetHeading -> strings.text(
            R.string.command_heading,
            Math.floorMod(command.headingDegrees.roundToInt(), 360),
        )
        is PlayerCommand.SetTargetAltitude -> strings.text(
            R.string.command_altitude,
            command.altitudeFeet.roundToInt(),
        )
        is PlayerCommand.SetTargetSpeed -> strings.text(
            R.string.command_speed,
            command.speedKnots.roundToInt(),
        )
        is PlayerCommand.DirectTo -> strings.text(
            R.string.command_direct,
            fixName(command.waypoint) ?: strings.text(R.string.route_point),
        )
        is PlayerCommand.SetRoute,
        is PlayerCommand.AppendWaypoint,
        -> strings.text(R.string.command_route)
        is PlayerCommand.UndoWaypoint -> strings.text(R.string.command_undo_waypoint)
        is PlayerCommand.ClearRoute -> strings.text(R.string.command_clear_route)
        is PlayerCommand.ClearToLand -> strings.text(
            R.string.command_clear_land_runway,
            command.runwayId,
        )
        is PlayerCommand.ClearForTakeoff -> strings.text(
            R.string.command_clear_takeoff_runway,
            command.runwayId,
        )
        is PlayerCommand.AssignRunway -> strings.text(
            R.string.command_assign_runway,
            command.runwayId,
        )
        is PlayerCommand.AssignApproach -> strings.text(
            R.string.command_assign_approach,
            command.runwayId,
        )
        is PlayerCommand.CancelApproach -> strings.text(R.string.command_cancel_approach)
        is PlayerCommand.LineUpAndWait -> strings.text(
            R.string.command_line_up,
            command.runwayId,
        )
        is PlayerCommand.CancelLandingClearance ->
            strings.text(R.string.command_cancel_landing)
        is PlayerCommand.CancelTakeoffClearance ->
            strings.text(R.string.command_cancel_takeoff)
        is PlayerCommand.GoAround -> strings.text(R.string.command_go_around)
        is PlayerCommand.AssignHold -> strings.text(
            R.string.command_hold,
            fixName(command.fix) ?: strings.text(R.string.route_point),
        )
        is PlayerCommand.CancelHold -> strings.text(R.string.command_cancel_hold)
        is PlayerCommand.IssueExitClearance -> strings.text(R.string.command_exit_clearance)
        is PlayerCommand.AcknowledgeInboundHandoff ->
            strings.text(R.string.command_accept_handoff)
        is PlayerCommand.InitiateOutboundHandoff ->
            strings.text(R.string.command_initiate_handoff)
        is PlayerCommand.CrossRunway -> strings.text(
            R.string.command_cross_runway,
            command.runwayId,
        )
        is PlayerCommand.AcknowledgeDynamicEvent ->
            strings.text(R.string.command_acknowledge_event)
        PlayerCommand.Start,
        PlayerCommand.Pause,
        PlayerCommand.Resume,
        is PlayerCommand.SetSimulationSpeed,
        -> strings.text(R.string.command_generic)
    }

    fun submitted(
        sequence: Long,
        aircraftId: String,
        callsign: String,
        command: PlayerCommand,
    ) = CommandReadbackUiModel(
        sequence = sequence,
        aircraftId = aircraftId,
        callsign = callsign,
        command = commandLabel(command),
        status = CommandReadbackStatus.SUBMITTED,
    )

    fun accepted(previous: CommandReadbackUiModel) = previous.copy(
        status = CommandReadbackStatus.ACCEPTED,
        detail = null,
    )

    fun rejected(previous: CommandReadbackUiModel, reason: String) = previous.copy(
        status = CommandReadbackStatus.REJECTED,
        detail = reason,
    )
}
