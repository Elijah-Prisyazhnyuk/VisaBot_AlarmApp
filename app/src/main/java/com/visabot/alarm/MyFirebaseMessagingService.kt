package com.visabot.alarm

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Подписываемся на топик при первом запуске
        FirebaseMessaging.getInstance().subscribeToTopic("alarm")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Если пришло data-сообщение
        if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: "Тревога!"
            val body = remoteMessage.data["body"] ?: "Найдены места"
            
            // ЗАПУСКАЕМ СИРЕНУ
            AlarmService.triggerAlarm(applicationContext, "$title\n$body")
        }
    }
}
