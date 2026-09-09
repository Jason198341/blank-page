package com.blank.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.blank.app.data.local.AppDatabase
import java.util.concurrent.TimeUnit

/**
 * 알람 재무장기.
 *
 * 항목마다 회차 4개를 다 걸지 않는다. 항목이 쌓이면 수백 개가 되고, Android 14+ 의
 * 정확 알람 쿼터와 재부팅 재등록 비용이 감당이 안 된다. 진실은 항상 DB 에 있고,
 * 알람은 "가장 빠른 미완료 회차 하나" 를 가리키는 포인터일 뿐이다.
 *
 * 발화·제출·항목 변경·재부팅·시간 변경 — 무엇이 일어나든 [resync] 하나로 수렴시킨다.
 */
object ScheduleSyncer {

    private const val REQ_NEXT = 1001
    private const val WORK_SAFETY = "blank_daily_safety"

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQ_NEXT,
            Intent(context, ReviewAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    suspend fun resync(context: Context) {
        val app = context.applicationContext
        val db = AppDatabase.get(app)
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(app)
        am.cancel(pi)

        val next = db.rounds().nextPending() ?: run { ensureSafetyNet(app); return }
        // 이미 지난 회차라면 60초 뒤에 깨워 밀린 것부터 처리하게 한다
        val fireAt = maxOf(next.dueAt, System.currentTimeMillis() + 60_000L)

        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        } else {
            // 정확 알람이 막혔으면 창을 주고라도 띄운다 (설정에서 사용자가 풀 수 있다)
            am.setWindow(AlarmManager.RTC_WAKEUP, fireAt, 15 * 60_000L, pi)
        }
        ensureSafetyNet(app)
    }

    /** 알람이 통째로 유실되는 상황(강제종료, OEM 킬러) 대비 하루 한 번 훑는다. */
    private fun ensureSafetyNet(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailySafetyWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_SAFETY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }
}
