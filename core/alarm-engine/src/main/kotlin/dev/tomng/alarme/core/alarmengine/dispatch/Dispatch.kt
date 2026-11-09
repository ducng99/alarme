package dev.tomng.alarme.core.alarmengine.dispatch

import dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery

/**
 * Seam between the delivery path (PI 1 receiver) and the ring host: a validated delivery is
 * dispatched here to start the foreground service.
 */
interface RingDispatcher {
    fun dispatch(delivery: RingDelivery)
}

/** Early-wake prepare actions (pre-alarm reminder + skip action). */
interface PrepareActions {
    fun onPrepare()
}

class NoopPrepareActions @javax.inject.Inject constructor() : PrepareActions {
    override fun onPrepare() = Unit
}

/** Wake-up check host (silent prompt service). */
interface WakeUpCheckDispatcher {
    fun onStartCheck(alarmId: Long)
}

class NoopWakeUpCheckDispatcher @javax.inject.Inject constructor() : WakeUpCheckDispatcher {
    override fun onStartCheck(alarmId: Long) = Unit
}
