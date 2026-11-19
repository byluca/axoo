package com.axoo.application.bootstrap

import com.axoo.core.push.PushNotifications
import com.axoo.core.push.PushRegistration
import com.axoo.core.push.PushTarget
import com.axoo.features.notifications.data.NotificationsRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class AxooMessagingService : FirebaseMessagingService() {
    @Inject lateinit var registration: PushRegistration
    @Inject lateinit var notifications: PushNotifications
    @Inject lateinit var inbox: NotificationsRepository

    override fun onRegistered(installationId: String) {
        runBlocking { withTimeoutOrNull(8_000) { registration.synchronize(installationId) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val target = PushTarget.from(message.data) ?: return
        runBlocking {
            withTimeoutOrNull(8_000) {
                if (registration.isCurrentRecipient(target.recipientId)) {
                    val body = message.notification?.body ?: message.data["message"]
                    if (body != null) notifications.show(target, message.data["type"] == "comment_reply", body)
                    inbox.received()
                }
            }
        }
    }
}
