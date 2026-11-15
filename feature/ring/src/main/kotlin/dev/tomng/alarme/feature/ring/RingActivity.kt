package dev.tomng.alarme.feature.ring

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
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
import dev.tomng.alarme.core.alarmengine.poweroff.PowerOffBlockerController
import dev.tomng.alarme.core.alarmengine.ring.RingScreenStarter
import dev.tomng.alarme.core.alarmengine.statemachine.RingEvent
import dev.tomng.alarme.core.alarmengine.statemachine.RingSessionStore
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.designsystem.theme.AlarmeTheme
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.ring.takeover.DisplayUsabilityProbe
import dev.tomng.alarme.feature.ring.takeover.InsetsSystemBarsHider
import dev.tomng.alarme.feature.ring.takeover.KeyVerdict
import dev.tomng.alarme.feature.ring.takeover.RingKeyPolicy
import dev.tomng.alarme.feature.ring.takeover.RingTakeoverController
import dev.tomng.alarme.feature.ring.takeover.ScreenWatchdog
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

    @Inject lateinit var ringScreenStarter: RingScreenStarter

    private val focusScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private lateinit var takeover: RingTakeoverController
    private lateinit var watchdog: ScreenWatchdog
    private lateinit var dialogCloser: SystemDialogCloser

    private val callbacks =
        object : RingScreenCallbacks {
            override fun onDismiss() = submit(RingEvent.OnDismissClick(clock.now()))

            override fun onSnooze() = submit(RingEvent.OnSnoozeClick(clock.now()))

            override fun onMissionStart() = submit(RingEvent.OnMissionStart)

            override fun onMissionComplete() = submit(RingEvent.OnMissionComplete(clock.now()))

            override fun onMissionTimerReset() = submit(RingEvent.OnMissionTimerReset)

            override fun onMissionTimerStop() = submit(RingEvent.OnMissionTimerStop)

            override fun onAddMissionTime() = submit(RingEvent.OnAddMissionTimeClick)

            override fun onWakeUpCheckComplete() = submit(RingEvent.OnWakeUpCheckComplete)

            override fun onEmergencyComplete() = submit(RingEvent.OnEmergencyComplete(clock.now()))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        takeover = RingTakeoverController(InsetsSystemBarsHider(window))
        takeover.takeOver(this)
        // Belt and braces: the key policy already swallows back in dispatchKeyEvent.
        onBackPressedDispatcher.addCallback(this) { /* swallowed */ }

        watchdog =
            ScreenWatchdog(
                clock = SystemClock::elapsedRealtime,
                isRinging = { sessionStore.state.value is RingState.Ringing },
                displaysUsable = DisplayUsabilityProbe(this),
                relaunch = { ringScreenStarter.start() },
            )
        dialogCloser = SystemDialogCloser(this)

        setContent {
            AlarmeTheme {
                val state by sessionStore.state.collectAsState()
                RingScreen(state = state, callbacks = callbacks)
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
            if (!state.keepsRingScreenAlive()) {
                delay(GHOST_FINISH_GRACE_MILLIS)
                if (!sessionStore.state.value.keepsRingScreenAlive() && !ringServiceRunning()) {
                    finish()
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
        this is RingState.Ringing || this is RingState.WakeUpCheck

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
                mission.needsCamera() && !hasPermission(Manifest.permission.CAMERA) ->
                    cameraPermission.launch(Manifest.permission.CAMERA)

                mission is Mission.WordBeat && !hasPermission(Manifest.permission.RECORD_AUDIO) ->
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun Mission.needsCamera(): Boolean =
        this is Mission.Barcode || this is Mission.Photo || this is Mission.ImageRecognition

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        takeover.keepImmersive()
        watchdog.onActivityShown()
        watchdog.start()
        dialogCloser.start()
    }

    override fun onPause() {
        dialogCloser.stop()
        watchdog.onActivityHidden()
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
        watchdog.onCheatDetected()
        focusScope.launch {
            blocker.onFocusLost(
                dismissed = sessionStore.state.value !is RingState.Ringing,
                screenKeeperPaused = !watchdog.isRunning,
            )
        }
    }

    override fun onDestroy() {
        focusScope.cancel()
        watchdog.stop()
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
