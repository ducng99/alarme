package dev.tomng.alarme.core.audio.volume

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The anti-mute core. The stream is silently forced (`flag 8`), re-forced on every
 * `Settings.System` change and every 5 s, and the user's original volume is restored on release.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VolumeLockTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val max: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    private val original: Int = (max / 2).coerceAtLeast(1)
    private val target: Int = (max - 1).coerceAtLeast(1)

    private var lock: VolumeLock? = null

    @Before
    fun setUp() {
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
    }

    @After
    fun tearDown() {
        lock?.release()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun newLock(): VolumeLock = VolumeLock(context).also { lock = it }

    private fun volume(): Int = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

    private fun idleFor(millis: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun `engage silently forces the target volume`() {
        newLock().engage(stream = AudioManager.STREAM_ALARM, target = max)
        assertThat(volume()).isEqualTo(max)
    }

    @Test
    fun `a user volume change is forced back`() {
        newLock().engage(stream = AudioManager.STREAM_ALARM, target = target)

        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)
        context.contentResolver.notifyChange(Settings.System.CONTENT_URI, null)
        // The shadow dispatches observer callbacks through the (paused) main looper; drain it
        // synchronously so the re-force is deterministic.
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(volume()).isEqualTo(target)
    }

    @Test
    fun `the five second tick re-forces the target`() {
        newLock().engage(stream = AudioManager.STREAM_ALARM, target = target)

        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        idleFor(5_000)

        assertThat(volume()).isEqualTo(target)
    }

    @Test
    fun `release restores the original volume`() {
        val lock = newLock()
        lock.engage(stream = AudioManager.STREAM_ALARM, target = max)

        lock.release()
        this.lock = null

        assertThat(volume()).isEqualTo(original)
    }

    @Test
    fun `release without engage is a no-op`() {
        newLock().release()
        assertThat(volume()).isEqualTo(original)
    }

    @Test
    fun `re-engaging switches target but still restores the first original`() {
        val lock = newLock()
        lock.engage(stream = AudioManager.STREAM_ALARM, target = max)
        lock.engage(stream = AudioManager.STREAM_ALARM, target = target)
        assertThat(volume()).isEqualTo(target)

        lock.release()
        this.lock = null

        assertThat(volume()).isEqualTo(original)
    }

    @Test
    fun `the tick keeps running while engaged`() {
        val lock = newLock()
        lock.engage(stream = AudioManager.STREAM_ALARM, target = target)

        // Three ticks in: the loop re-posted itself each time and still re-forces.
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        idleFor(15_000)
        assertThat(volume()).isEqualTo(target)

        lock.release()
        this.lock = null

        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)
        idleFor(15_000)
        assertThat(volume()).isEqualTo(1) // released: no more re-forcing
    }

    @Test
    fun `duck lowers then restores the locked target`() {
        val lock = newLock()
        lock.engage(stream = AudioManager.STREAM_ALARM, target = max)
        val ducked = (max / 4).coerceAtLeast(1)

        lock.duck(target = ducked)
        assertThat(volume()).isEqualTo(ducked)

        idleFor(6_000) // tick must keep the DUCKED target, not snap back
        assertThat(volume()).isEqualTo(ducked)

        lock.unduck()
        assertThat(volume()).isEqualTo(max)
    }

    @Test
    fun `duck without engage is a no-op`() {
        newLock().duck(target = 1)
        assertThat(volume()).isEqualTo(original)
    }

    @Test
    fun `escalate raises the locked target but restore keeps the original`() {
        val lock = newLock()
        lock.engage(stream = AudioManager.STREAM_ALARM, target = target)

        lock.escalate(target = max)
        assertThat(volume()).isEqualTo(max)

        idleFor(6_000) // the tick holds the escalated target
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)
        idleFor(6_000)
        assertThat(volume()).isEqualTo(max)

        lock.release()
        this.lock = null

        assertThat(volume()).isEqualTo(original)
    }
}
