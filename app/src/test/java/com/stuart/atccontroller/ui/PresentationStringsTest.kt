package com.stuart.atccontroller.ui

import com.stuart.atccontroller.R
import com.stuart.atccontroller.simulation.DynamicEventLifecycle
import com.stuart.atccontroller.simulation.HandoffStatus
import com.stuart.atccontroller.simulation.WeatherState
import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationStringsTest {
    private val strings = StringResolver { id, args ->
        when (id) {
            R.string.wind_calm -> "Calm"
            R.string.wind_direction_speed -> "${args[0]}° / ${args[1]} kt"
            R.string.visibility_km -> "${args[0]} km"
            else -> error("Unexpected resource $id")
        }
    }
    private val presenter = WeatherPresenter(strings)

    @Test
    fun calmAndLiveWindUseOneCanonicalFormatter() {
        assertEquals("Calm", presenter.wind(WeatherState(windSpeedKnots = 0.49)))
        assertEquals(
            "005° / 08 kt",
            presenter.wind(
                WeatherState(
                    windDirectionDegrees = 364.6,
                    windSpeedKnots = 8.2,
                    visibilityKm = 11.6,
                ),
            ),
        )
        assertEquals(
            "12 km",
            presenter.visibility(WeatherState(visibilityKm = 11.6)),
        )
    }

    @Test
    fun everyHandoffAndDynamicLifecycleHasAResourceBackedLabel() {
        assertEquals(
            HandoffStatus.entries.size,
            HandoffStatus.entries.map(HandoffStatus::labelResource).distinct().size,
        )
        assertEquals(
            DynamicEventLifecycle.entries.size,
            DynamicEventLifecycle.entries
                .map(DynamicEventLifecycle::labelResource)
                .distinct()
                .size,
        )
    }
}
