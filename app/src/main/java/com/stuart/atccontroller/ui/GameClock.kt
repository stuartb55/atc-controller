package com.stuart.atccontroller.ui

import java.time.LocalDate

/** Replaceable wall-clock boundary for daily content and durable record timestamps. */
internal interface GameClock {
    fun today(): LocalDate
    fun currentTimeMillis(): Long
}

internal object SystemGameClock : GameClock {
    override fun today(): LocalDate = LocalDate.now()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
