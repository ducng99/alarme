package dev.tomng.alarme.feature.backup

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.repository.BackupRepository
import dev.tomng.alarme.core.data.ringtone.RingtonePaths
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object BackupModule {

    @Provides
    @Singleton
    fun backupManager(
        @ApplicationContext context: Context,
        repository: BackupRepository,
        clock: AppClock,
        scheduler: AlarmScheduler,
    ): BackupManager =
        BackupManager(
            contentResolver = context.contentResolver,
            repository = repository,
            codec = BackupCodec(),
            paths = RingtonePaths(context),
            clock = clock,
            scheduler = scheduler,
        )

    @Provides fun backupOperations(manager: BackupManager): BackupOperations = manager
}
