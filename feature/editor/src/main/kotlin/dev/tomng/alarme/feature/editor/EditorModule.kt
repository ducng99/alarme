package dev.tomng.alarme.feature.editor

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The editor has no bindings of its own yet; the module exists so the graph stays explicit. */
@Module @InstallIn(SingletonComponent::class) object EditorModule
