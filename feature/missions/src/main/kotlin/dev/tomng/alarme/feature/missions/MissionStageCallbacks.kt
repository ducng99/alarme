package dev.tomng.alarme.feature.missions

/** The events a mission screen reports up to the ring session. */
interface MissionStageCallbacks {

    /** The mission finished — advance the chain or dismiss. */
    fun onComplete()

    /**
     * User activity (a keystroke, a tap, sensor progress, a listen attempt): restart the countdown.
     */
    fun onTimerReset()
}
