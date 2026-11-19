package com.axoo.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.axoo.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PushTarget(val postId: String, val commentId: String, val recipientId: String) {
    companion object {
        fun from(data: Map<String, String>): PushTarget? = runCatching {
            require(data["type"] in setOf("post_comment", "comment_reply"))
            PushTarget(
                UUID.fromString(data.getValue("post_id")).toString(),
                UUID.fromString(data.getValue("comment_id")).toString(),
                UUID.fromString(data.getValue("recipient_id")).toString(),
            )
        }.getOrNull()

        fun from(intent: Intent): PushTarget? = from(
            listOf("type", "post_id", "comment_id", "recipient_id")
                .mapNotNull { key -> intent.getStringExtra(key)?.let { key to it } }.toMap(),
        )
    }
}

@Singleton
class PushNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Commenti e risposte", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    fun isEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun clear() = NotificationManagerCompat.from(context).cancelAll()

    fun show(target: PushTarget, isReply: Boolean, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!isEnabled()) return
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.data = Uri.parse("axoo://push/${target.recipientId}/${target.commentId}")
        intent.putExtra("type", if (isReply) "comment_reply" else "post_comment")
        intent.putExtra("post_id", target.postId)
        intent.putExtra("comment_id", target.commentId)
        intent.putExtra("recipient_id", target.recipientId)
        val tap = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_axoo)
            .setContentTitle("Axoo")
            .setContentText(message)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(target.commentId, 0, notification)
    }

    companion object { const val CHANNEL_ID = "interactions" }
}
