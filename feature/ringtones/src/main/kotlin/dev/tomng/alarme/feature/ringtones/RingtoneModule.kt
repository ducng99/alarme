package dev.tomng.alarme.feature.ringtones

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tomng.alarme.core.data.repository.RingtoneRepository
import dev.tomng.alarme.core.data.ringtone.RingtonePaths
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RingtoneModule {

    @Provides
    @Singleton
    fun ringtoneImporter(
        @ApplicationContext context: Context,
        catalog: RingtoneRepository,
    ): RingtoneImporter =
        CopyingRingtoneImporter(
            paths = RingtonePaths(context),
            source = ContentResolverDocumentSource(context.contentResolver),
            probe = MediaMetadataRetrieverProbe(),
            catalog = catalog,
        )

    @Provides
    @Singleton
    fun deviceRingtoneSource(@ApplicationContext context: Context): DeviceRingtoneSource =
        DeviceRingtoneSource(RingtoneManagerReader(context))

    @Provides
    @Singleton
    fun previewPlayer(@ApplicationContext context: Context): PreviewPlayer =
        ExoPreviewPlayer(context)

    @Provides
    @Singleton
    fun ringtoneRecorder(
        @ApplicationContext context: Context,
        catalog: RingtoneRepository,
    ): RingtoneRecorder =
        RingtoneRecorder(
            paths = RingtonePaths(context),
            engine = MediaRecorderEngine(context),
            probe = MediaMetadataRetrieverProbe(),
            catalog = catalog,
        )
}
