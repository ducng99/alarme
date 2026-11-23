package dev.tomng.alarme.feature.ring

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import dev.tomng.alarme.core.alarmengine.notifications.AlarmNotifications
import dev.tomng.alarme.core.alarmengine.poweroff.PowerOffBlockerController
import dev.tomng.alarme.core.alarmengine.ring.RingScreenKeeper
import dev.tomng.alarme.core.alarmengine.statemachine.RingEvent
import dev.tomng.alarme.core.alarmengine.statemachine.RingSessionStore
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.designsystem.theme.AlarmeTheme
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.ring.takeover.InsetsSystemBarsHider
import dev.tomng.alarme.feature.ring.takeover.KeyVerdict
import dev.tomng.alarme.feature.ring.takeover.RingKeyPolicy
import dev.tomng.alarme.feature.ring.takeover.RingTakeoverController
import dev.tomng.alarme.feature.ring.takeover.SystemDialogCloser
import dev.tomng.alarme.feature.ring.ui.RingScreen
import dev.tomng.alarme.feature.ring.ui.RingScreenCallbacks
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The full-screen ring takeover: wakes the screen over the lock, keeps it on, hides the bars,
 * swallows back and volume keys, and pulls itself back to the front if the user navigates away.
 * Everything the user does here is routed into the shared [RingSessionStore]; the UI itself is a
 * pure function of the session state.
 */
@AndroidEntryPoint
class RingActivity : ComponentActivity() {

    @Inject lateinit var sessionStore: RingSessionStore

    @Inject lateinit var clock: AppClock

    @Inject lateinit var blocker: PowerOffBlockerController

    @Inject lateinit var screenKeeper: RingScreenKeeper

    @Inject lateinit var notifications: AlarmNotifications

    private val focusScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // The system permission dialog pauses this activity; it must not count as leaving the ring.
    private var awaitingPermission = false
    private var hasShownSession = false

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            awaitingPermission = false
        }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            awaitingPermission = false
        }

    private lateinit var takeover: RingTakeoverController
    private lateinit var dialogCloser: SystemDialogCloser

    private val callbacks =
        object : RingScreenCallbacks {
            override fun onDismiss() = submit(RingEvent.OnDismissClick(clock.now()))

            override fun onSnooze() = submit(RingEvent.OnSnoozeClick(clock.now()))

            override fun onMissionStart() = submit(RingEvent.OnMissionStart(clock.now()))

            override fun onMissionComplete() = submit(RingEvent.OnMissionComplete(clock.now()))

            override fun onMissionTimerReset() = submit(RingEvent.OnMissionTimerReset)

            override fun onWakeUpCheckComplete() = submit(RingEvent.OnWakeUpCheckComplete)

            override fun onEmergency() = submit(RingEvent.OnEmergencyClick(clock.now()))

            override fun onEmergencyTap() = submit(RingEvent.OnEmergencyTap(clock.now()))

            override fun onEmergencyCancel() = submit(RingEvent.OnEmergencyCancel(clock.now()))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        takeover = RingTakeoverController(InsetsSystemBarsHider(window))
        takeover.takeOver(this)
        // Belt and braces: the key policy already swallows back in dispatchKeyEvent.
        onBackPressedDispatcher.addCallback(this) { /* swallowed */ }

        dialogCloser = SystemDialogCloser(this)

        setContent {
            AlarmeTheme {
                val state by sessionStore.state.collectAsState()
                RingScreen(state = state, callbacks = callbacks)
                val holdScreenOn = RingTakeoverController.holdsScreenOn(state)
                LaunchedEffect(holdScreenOn) {
                    takeover.holdScreenOn(this@RingActivity, holdScreenOn)
                }
                FinishWhenSessionEnds(state)
                RequestMissionPermission(state)
            }
        }
    }

    /**
     * A ring screen without a ringing session is a ghost (stale notification tap): wait out the
     * race, then close. The effect restarts on every state change, so a session appearing cancels
     * the countdown — and the ring service still starting up (its alarm lookup can take longer than
     * the grace on a cold device) also keeps the screen alive.
     */
    @Composable
    private fun FinishWhenSessionEnds(state: RingState) {
        LaunchedEffect(state) {
            val alive = state.keepsRingScreenAlive()
            if (alive) hasShownSession = true
            when (RingFinishPolicy.decide(alive, hasShownSession)) {
                FinishDecision.STAY -> Unit
                FinishDecision.IMMEDIATE -> finish()
                FinishDecision.AFTER_GRACE -> {
                    delay(GHOST_FINISH_GRACE_MILLIS)
                    if (!sessionStore.state.value.keepsRingScreenAlive() && !ringServiceRunning()) {
                        finish()
                    }
                }
            }
        }
    }

    /** Own services only — exactly what we need: is the alarm still ringing behind this screen? */
    @Suppress("DEPRECATION")
    private fun ringServiceRunning(): Boolean =
        getSystemService(android.app.ActivityManager::class.java)
            ?.getRunningServices(Int.MAX_VALUE)
            .orEmpty()
            .any { it.service.className == ALARM_SERVICE_NAME }

    /** The screen lives while the alarm rings and through the 100 s check confirmation. */
    private fun RingState.keepsRingScreenAlive(): Boolean =
        RingFinishPolicy.keepsRingScreenAlive(this)

    /**
     * Camera and microphone missions prompt in place as they start: the ring is already full-screen
     * over the lock, and the mission screens degrade gracefully when the permission is refused (the
     * emergency path stays available).
     */
    @Composable
    private fun RequestMissionPermission(state: RingState) {
        val mission = ((state as? RingState.Ringing)?.stage as? RingStage.Mission)?.mission
        LaunchedEffect(mission) {
            when {
                mission == null -> Unit
                mission.needsCamera() && !hasPermission(Manifest.permission.CAMERA) -> {
                    awaitingPermission = true
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }

                mission is Mission.WordBeat && !hasPermission(Manifest.permission.RECORD_AUDIO) -> {
                    awaitingPermission = true
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    private fun Mission.needsCamera(): Boolean = this is Mission.Barcode || this is Mission.Photo

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        takeover.keepImmersive()
        screenKeeper.onScreenShown()
        notifications.onRingScreenShown()
        dialogCloser.start()
    }

    override fun onPause() {
        dialogCloser.stop()
        if (!awaitingPermission) {
            screenKeeper.onScreenHidden()
            notifications.onRingScreenHidden()
        }
        super.onPause()
    }

    /**
     * Losing focus while ringing is the cheat signal: the user opened the power menu, the recents
     * sheet, or another app. The blocker decides (settings + ring state) and the accessibility
     * service, if granted, presses BACK/HOME to pull the ring back.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) return
        focusScope.launch {
            blocker.onFocusLost(
                dismissed = sessionStore.state.value !is RingState.Ringing,
                screenKeeperPaused = !screenKeeper.isRunning,
            )
        }
    }

    override fun onDestroy() {
        focusScope.cancel()
        screenKeeper.onScreenHidden()
        dialogCloser.stop()
        takeover.release(this)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        when (RingKeyPolicy.onKey(keyCode, sessionStore.volumeKeyBypass)) {
            KeyVerdict.CONSUME -> true
            KeyVerdict.CONSUME_AND_MUTE -> {
                submit(RingEvent.VolumeMuteRequested)
                true
            }
            KeyVerdict.PASS -> super.onKeyDown(keyCode, event)
        }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        when (RingKeyPolicy.onKey(keyCode, sessionStore.volumeKeyBypass)) {
            KeyVerdict.CONSUME,
            KeyVerdict.CONSUME_AND_MUTE -> true
            KeyVerdict.PASS -> super.onKeyUp(keyCode, event)
        }

    private fun submit(event: RingEvent) {
        sessionStore.submit(event)
    }

    companion object {
        private const val GHOST_FINISH_GRACE_MILLIS = 2_000L
        private const val ALARM_SERVICE_NAME =
            "dev.tomng.alarme.core.alarmengine.service.AlarmService"
    }
}
