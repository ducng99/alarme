package dev.tomng.alarme.core.common.di

import javax.inject.Qualifier

/** Process-wide scope for fire-and-forget application work (receivers, repositories). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
