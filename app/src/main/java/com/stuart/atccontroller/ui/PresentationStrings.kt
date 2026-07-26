package com.stuart.atccontroller.ui

import android.content.res.Resources
import com.stuart.atccontroller.R
import com.stuart.atccontroller.simulation.DynamicEventLifecycle
import com.stuart.atccontroller.simulation.HandoffStatus
import com.stuart.atccontroller.simulation.WeatherState
import java.util.Locale
import kotlin.math.roundToInt

/** Keeps Android resources at the presentation boundary and replaceable in mapper tests. */
internal fun interface StringResolver {
    fun resolve(id: Int, args: Array<out Any>): String
}

internal class AndroidStringResolver(
    private val resources: Resources,
) : StringResolver {
    override fun resolve(id: Int, args: Array<out Any>): String = resources.getString(id, *args)
}

internal fun StringResolver.text(id: Int, vararg args: Any): String = resolve(id, args)

internal class WeatherPresenter(
    private val strings: StringResolver,
) {
    fun wind(weather: WeatherState): String {
        if (weather.windSpeedKnots < CALM_THRESHOLD_KNOTS) {
            return strings.text(R.string.wind_calm)
        }
        val direction = normalizedDirection(weather.windDirectionDegrees)
        val speed = weather.windSpeedKnots.roundToInt().coerceAtLeast(0)
        return strings.text(
            R.string.wind_direction_speed,
            String.format(Locale.ROOT, "%03d", direction),
            String.format(Locale.ROOT, "%02d", speed),
        )
    }

    fun visibility(weather: WeatherState): String = strings.text(
        R.string.visibility_km,
        weather.visibilityKm.roundToInt().coerceAtLeast(0),
    )

    private fun normalizedDirection(degrees: Double): Int =
        Math.floorMod(degrees.roundToInt(), FULL_CIRCLE_DEGREES)

    private companion object {
        const val CALM_THRESHOLD_KNOTS = 0.5
        const val FULL_CIRCLE_DEGREES = 360
    }
}

internal fun HandoffStatus.labelResource(): Int = when (this) {
    HandoffStatus.OFFERED -> R.string.handoff_offered
    HandoffStatus.REQUESTED -> R.string.handoff_requested
    HandoffStatus.ACKNOWLEDGED -> R.string.handoff_acknowledged
    HandoffStatus.COMPLETED -> R.string.handoff_completed
    HandoffStatus.TIMED_OUT -> R.string.handoff_timed_out
}

internal fun DynamicEventLifecycle.labelResource(): Int = when (this) {
    DynamicEventLifecycle.SCHEDULED -> R.string.dynamic_state_scheduled
    DynamicEventLifecycle.WARNING -> R.string.dynamic_state_warning
    DynamicEventLifecycle.ACTIVE -> R.string.dynamic_state_active
    DynamicEventLifecycle.RECOVERY -> R.string.dynamic_state_recovery
    DynamicEventLifecycle.RESOLVED -> R.string.dynamic_state_resolved
    DynamicEventLifecycle.FAILED -> R.string.dynamic_state_failed
}
