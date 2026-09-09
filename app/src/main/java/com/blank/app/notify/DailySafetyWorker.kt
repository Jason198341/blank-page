package com.blank.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 하루 한 번 훑는 안전망. 정확 알람이 유실됐어도 밀린 회차가 조용히 사라지지는 않게 한다.
 * 이게 주 엔진이 아니다 — WorkManager 는 Doze 에서 수십 분씩 밀리므로 "그 시각" 을 못 지킨다.
 */
class DailySafetyWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { ReviewDispatcher.fireDue(applicationContext) }
        runCatching { ScheduleSyncer.resync(applicationContext) }
        return Result.success()
    }
}
