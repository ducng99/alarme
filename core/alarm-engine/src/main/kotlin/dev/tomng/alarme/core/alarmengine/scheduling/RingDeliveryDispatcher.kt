package dev.tomng.alarme.core.alarmengine.scheduling

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.alarmengine.dispatch.RingDispatcher
import dev.tomng.alarme.core.alarmengine.ring.AlarmServiceIntents
import dev.tomng.alarme.core.alarmengine.ring.RingServiceStarter
import dev.tomng.alarme.core.common.time.AppClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/** Default [RingServiceStarter]: starts the FGS from an active broadcast context. */
class ContextRingServiceStarter
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : dev.tomng.alarme.core.alarmengine.ring.RingServiceStarter {
    override fun start(intent: Intent) {
        context.startForegroundService(intent)
    }
}

/**
 * Bridges validated deliveries (PI 1) to the ring service, with a single retry: a blocked FGS start
 * within 55 s of the alarm time re-arms at now + 3 s with the retry flag.
 */
@Singleton
class RingDeliveryDispatcher
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val starter: RingServiceStarter,
    private val scheduler: AlarmScheduler,
    private val clock: AppClock,
) : RingDispatcher {

    override fun dispatch(delivery: RingDelivery) {
        val intent = AlarmServiceIntents.serviceIntent(context, delivery)
        try {
            starter.start(intent)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            onBlockedStart(e, delivery)
        }
    }

    private fun onBlockedStart(
        e: ForegroundServiceStartNotAllowedException,
        delivery: RingDelivery,
    ) {
        val scheduledAt = Instant.fromEpochMilliseconds(delivery.alarmTimeMillis)
        val shouldRetry =
            Build.VERSION.SDK_INT >= 31 &&
                StartFailurePolicy.shouldRetry(clock.now(), scheduledAt, delivery.isRetry)
        if (shouldRetry) {
            scheduler.armRetryRing(delivery, clock.now() + StartFailurePolicy.RETRY_DELAY)
        } else {
            throw e
        }
    }
}
