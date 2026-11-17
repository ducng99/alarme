package dev.tomng.alarme.core.alarmengine.di

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tomng.alarme.core.alarmengine.dispatch.AndroidWakeUpCheckDispatcher
import dev.tomng.alarme.core.alarmengine.dispatch.PrepareActions
import dev.tomng.alarme.core.alarmengine.dispatch.ReminderActions
import dev.tomng.alarme.core.alarmengine.dispatch.RingDispatcher
import dev.tomng.alarme.core.alarmengine.dispatch.WakeUpCheckDispatcher
import dev.tomng.alarme.core.alarmengine.notifications.RingScreenIntentFactory
import dev.tomng.alarme.core.alarmengine.ring.RingServiceStarter
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmSchedulerImpl
import dev.tomng.alarme.core.alarmengine.scheduling.ContextRingServiceStarter
import dev.tomng.alarme.core.alarmengine.scheduling.DeviceProtectedScheduleIdStore
import dev.tomng.alarme.core.alarmengine.scheduling.RingDeliveryDispatcher
import dev.tomng.alarme.core.alarmengine.scheduling.ScheduleIdStore
import dev.tomng.alarme.core.alarmengine.scheduling.TimeChangeGuard
import dev.tomng.alarme.core.alarmengine.scheduling.WarmUpPolicy
import dev.tomng.alarme.core.alarmengine.scheduling.WarmUpRule
import dev.tomng.alarme.core.common.time.AppClock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SchedulingModule {

    @Binds abstract fun bindAlarmScheduler(impl: AlarmSchedulerImpl): AlarmScheduler

    @Binds
    @Singleton
    abstract fun bindBlockerChannel(
        impl: dev.tomng.alarme.core.alarmengine.poweroff.AccessibilityBlockerChannel,
    ): dev.tomng.alarme.core.alarmengine.poweroff.BlockerChannel

    @Binds
    @Singleton
    abstract fun bindUninstallGuard(
        impl: dev.tomng.alarme.core.alarmengine.uninstall.DeviceAdminController,
    ): dev.tomng.alarme.core.alarmengine.uninstall.UninstallGuard

    @Binds
    @Singleton
    abstract fun bindBlockCounterStore(
        impl: dev.tomng.alarme.core.alarmengine.poweroff.PrefsBlockCounterStore,
    ): dev.tomng.alarme.core.alarmengine.poweroff.BlockCounterStore

    @Binds
    @Singleton
    abstract fun bindSnoozeStore(
        impl: dev.tomng.alarme.core.alarmengine.scheduling.DeviceProtectedSnoozeStore,
    ): dev.tomng.alarme.core.alarmengine.scheduling.SnoozeStore

    @Binds abstract fun bindScheduleIdStore(impl: DeviceProtectedScheduleIdStore): ScheduleIdStore

    @Binds @Singleton abstract fun bindRingDispatcher(impl: RingDeliveryDispatcher): RingDispatcher

    @Binds abstract fun bindRingServiceStarter(impl: ContextRingServiceStarter): RingServiceStarter

    @Binds
    @Singleton
    abstract fun bindRingScreenStarter(
        impl: dev.tomng.alarme.core.alarmengine.ring.ContextRingScreenStarter,
    ): dev.tomng.alarme.core.alarmengine.ring.RingScreenStarter

    @Binds
    abstract fun bindDisplayProbe(
        impl: dev.tomng.alarme.core.alarmengine.ring.AndroidDisplayProbe,
    ): dev.tomng.alarme.core.alarmengine.ring.DisplayProbe

    @Binds @Singleton abstract fun bindPrepareActions(impl: ReminderActions): PrepareActions

    @Binds
    @Singleton
    abstract fun bindWakeUpCheckDispatcher(
        impl: AndroidWakeUpCheckDispatcher,
    ): WakeUpCheckDispatcher

    @Binds
    @Singleton
    abstract fun bindSkipAlarmAction(
        impl: dev.tomng.alarme.core.alarmengine.dispatch.UseCaseSkipAlarmAction,
    ): dev.tomng.alarme.core.alarmengine.dispatch.SkipAlarmAction

    @Binds
    @Singleton
    abstract fun bindCheckVibrator(
        impl: dev.tomng.alarme.core.alarmengine.ring.AndroidCheckVibrator,
    ): dev.tomng.alarme.core.alarmengine.ring.CheckVibrator
}

@Module
@InstallIn(SingletonComponent::class)
internal object SchedulingProviderModule {

    @Provides
    @Singleton
    fun provideRingtoneResolver(
        catalog: dev.tomng.alarme.core.data.repository.RingtoneRepository,
        settings: dev.tomng.alarme.core.data.repository.SettingsRepository,
    ): dev.tomng.alarme.core.alarmengine.ring.RingtoneResolver =
        dev.tomng.alarme.core.alarmengine.ring.CatalogRingtoneResolver(
            catalog = catalog,
            settings = settings,
            fileExists = { uri ->
                val path = runCatching { java.net.URI(uri).path }.getOrNull()
                path != null && java.io.File(path).exists()
            },
            pick = { count -> kotlin.random.Random.nextInt(count) },
        )

    @Provides
    @Singleton
    fun alarmManager(@ApplicationContext context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    @Provides
    @Singleton
    fun armTarget(
        alarmManager: AlarmManager,
    ): dev.tomng.alarme.core.alarmengine.scheduling.ArmTarget =
        dev.tomng.alarme.core.alarmengine.scheduling.AndroidArmTarget(alarmManager)

    @Provides
    @Singleton
    fun warmUpRule(): WarmUpRule = WarmUpRule {
        WarmUpPolicy.ruleFor(Build.MANUFACTURER, Build.VERSION.SDK_INT)
    }

    @Provides
    @Singleton
    fun timeChangeGuard(clock: AppClock): TimeChangeGuard = TimeChangeGuard(clock)

    @Provides
    @Singleton
    fun ringScreenIntentFactory(@ApplicationContext context: Context): RingScreenIntentFactory =
        RingScreenIntentFactory {
            dev.tomng.alarme.core.alarmengine.ring.RingIntents.openRingIntent(context.packageName)
        }
}
