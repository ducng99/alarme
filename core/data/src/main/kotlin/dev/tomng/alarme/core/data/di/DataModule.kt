package dev.tomng.alarme.core.data.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tomng.alarme.core.common.di.ApplicationScope
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.common.time.SystemAppClock
import dev.tomng.alarme.core.data.db.AlarmeDatabase
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.core.data.repository.AlarmRepositoryImpl
import dev.tomng.alarme.core.data.repository.BackupRepository
import dev.tomng.alarme.core.data.repository.BackupRepositoryImpl
import dev.tomng.alarme.core.data.repository.RingEventRepository
import dev.tomng.alarme.core.data.repository.RingEventRepositoryImpl
import dev.tomng.alarme.core.data.repository.RingtoneRepository
import dev.tomng.alarme.core.data.repository.RingtoneRepositoryImpl
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.core.data.repository.SettingsRepositoryImpl
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataBindsModule {

    @Binds @Singleton abstract fun alarmRepository(impl: AlarmRepositoryImpl): AlarmRepository

    @Binds @Singleton abstract fun backupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun ringEventRepository(impl: RingEventRepositoryImpl): RingEventRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object DataProviderModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AlarmeDatabase =
        Room.databaseBuilder(context, AlarmeDatabase::class.java, DATABASE_NAME).build()

    @Provides fun alarmDao(db: AlarmeDatabase) = db.alarmDao()

    @Provides fun missionDao(db: AlarmeDatabase) = db.missionDao()

    @Provides fun typingPhraseDao(db: AlarmeDatabase) = db.typingPhraseDao()

    @Provides fun barcodeDao(db: AlarmeDatabase) = db.barcodeDao()

    @Provides fun ringtoneDao(db: AlarmeDatabase) = db.ringtoneDao()

    @Provides fun alarmEventDao(db: AlarmeDatabase) = db.alarmEventDao()

    @Provides @Singleton fun appClock(): AppClock = SystemAppClock()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun settingsFile(@ApplicationContext context: Context): File =
        context.preferencesDataStoreFile("alarme_settings")

    private const val DATABASE_NAME = "alarme.db"
}

@Module
@InstallIn(SingletonComponent::class)
internal object RingtoneProviderModule {

    @Provides
    @Singleton
    fun ringtoneRepository(dao: dev.tomng.alarme.core.data.dao.RingtoneDao): RingtoneRepository =
        RingtoneRepositoryImpl(dao)
}
