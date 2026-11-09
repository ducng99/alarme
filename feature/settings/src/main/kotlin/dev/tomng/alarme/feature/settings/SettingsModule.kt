package dev.tomng.alarme.feature.settings

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomng.alarme.feature.settings.diagnostics.AndroidDiagnosticsReader
import dev.tomng.alarme.feature.settings.diagnostics.DiagnosticsReader
import dev.tomng.alarme.feature.settings.permissions.AndroidPermissionIntents
import dev.tomng.alarme.feature.settings.permissions.AndroidPermissionProbe
import dev.tomng.alarme.feature.settings.permissions.PermissionIntents
import dev.tomng.alarme.feature.settings.permissions.PermissionProbe
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsModule {

    @Binds @Singleton abstract fun permissionProbe(impl: AndroidPermissionProbe): PermissionProbe

    @Binds
    @Singleton
    abstract fun permissionIntents(impl: AndroidPermissionIntents): PermissionIntents

    @Binds
    @Singleton
    abstract fun diagnosticsReader(impl: AndroidDiagnosticsReader): DiagnosticsReader
}
