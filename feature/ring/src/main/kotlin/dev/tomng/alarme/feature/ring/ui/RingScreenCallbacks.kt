package dev.tomng.alarme.feature.ring.ui

/** The user intents the ring screen can emit. */
interface RingScreenCallbacks {
    fun onDismiss()

    fun onSnooze()

    fun onMissionStart()

    fun onMissionComplete()

    /** User activity inside the mission (a keystroke, frames analysed): restart the countdown. */
    fun onMissionTimerReset()

    /** Give up on the mission: open the tap-to-dismiss emergency. */
    fun onEmergency()

    fun onEmergencyTap()

    /** Back from the emergency to the mission. */
    fun onEmergencyCancel()

    /** The wake-up check's "Yes, I'm up!" — the only way out of the 100 s window. */
    fun onWakeUpCheckComplete()
}
