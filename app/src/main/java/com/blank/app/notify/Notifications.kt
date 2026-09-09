package com.blank.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blank.app.MainActivity
import com.blank.app.R
import com.blank.app.domain.ReviewSchedule

/**
 * 알림에는 **제목과 회차만** 넣는다. 본문이 한 글자라도 새면 그건 힌트고,
 * 힌트를 본 재현은 인출이 아니다.
 */
object Notifications {

    const val CHANNEL_ID = "recall"
    private const val CHANNEL_NAME = "인출 알림"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "오늘 백지에 재현할 지식을 알려줍니다"
            enableVibration(true)
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun show(context: Context, roundId: Long, itemTitle: String, roundIndex: Int) {
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROUND_ID, roundId)
        }
        val pending = PendingIntent.getActivity(
            context, roundId.toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(itemTitle)
            .setContentText("${ReviewSchedule.label(roundIndex)} · 빈 종이에 재현할 차례")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(roundId.toInt(), notification)
        }
    }

    fun cancel(context: Context, roundId: Long) {
        runCatching { NotificationManagerCompat.from(context).cancel(roundId.toInt()) }
    }
}
