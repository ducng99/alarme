package dev.tomng.alarme.core.audio.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tomng.alarme.core.alarmengine.ring.AudioOutputDetector
import dev.tomng.alarme.core.alarmengine.ring.RingHost
import dev.tomng.alarme.core.audio.device.ExternalAudioOutputDetector
import dev.tomng.alarme.core.audio.host.RingAudioHost
import dev.tomng.alarme.core.audio.player.AlarmPlayerFactory
import dev.tomng.alarme.core.audio.player.Media3AlarmPlayer
import dev.tomng.alarme.core.audio.tts.AndroidTtsSpeaker
import dev.tomng.alarme.core.audio.tts.TtsSpeaker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AudioModule {

    @Binds @Singleton abstract fun bindRingHost(impl: RingAudioHost): RingHost

    @Binds @Singleton abstract fun bindTtsSpeaker(impl: AndroidTtsSpeaker): TtsSpeaker

    @Binds
    @Singleton
    abstract fun bindAudioOutputDetector(impl: ExternalAudioOutputDetector): AudioOutputDetector
}

@Module
@InstallIn(SingletonComponent::class)
internal object AudioProviderModule {

    @Provides
    @Singleton
    fun alarmPlayerFactory(@ApplicationContext context: Context): AlarmPlayerFactory =
        AlarmPlayerFactory {
            Media3AlarmPlayer(context)
        }
}
