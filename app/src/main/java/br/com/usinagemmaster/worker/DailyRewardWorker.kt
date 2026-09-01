package br.com.usinagemmaster.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Base para recompensas/eventos periódicos. O motor idle principal NÃO depende deste Worker:
 * ele calcula o tempo decorrido usando timestamps quando o jogador retorna ao app.
 */
@HiltWorker
class DailyRewardWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
