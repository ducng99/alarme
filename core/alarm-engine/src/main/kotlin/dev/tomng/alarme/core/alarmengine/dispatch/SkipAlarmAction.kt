package dev.tomng.alarme.core.alarmengine.dispatch

import dev.tomng.alarme.core.common.di.ApplicationScope
import dev.tomng.alarme.core.data.repository.SkipAlarmUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The reminder action's work, behind a seam so the receiver stays a shell. */
fun interface SkipAlarmAction {
    fun skip(alarmId: Long)
}

@Singleton
class UseCaseSkipAlarmAction
@Inject
constructor(
    private val useCase: SkipAlarmUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) : SkipAlarmAction {

    override fun skip(alarmId: Long) {
        scope.launch { useCase.skip(alarmId) }
    }
}
