package com.visabot.alarm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    
    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM", "📩 Получено сообщение от Firebase")
        Log.d("FCM", "Data: ${message.data}")
        Log.d("FCM", "Notification: ${message.notification?.title}")
        
        // Проверяем data payload (приходит от Python бота)
        val data = message.data
        
        // Проверяем флаг будильника
        if (data["alarm_trigger"] == "true") {
            val title = data["title"] ?: message.notification?.title ?: "МЕСТА ПОЯВИЛИСЬ!"
            val body = data["body"] ?: message.notification?.body ?: "Срочно проверьте!"
            val keyword = data["keyword"] ?: ""
            
            Log.d("FCM", "🚨 Обнаружен флаг будильника! Запускаем алерт...")
            
            // Запускаем будильник
            val fullMessage = "$title\n$body"
            AlarmService.triggerAlarm(applicationContext, fullMessage)
        } else {
            Log.d("FCM", "ℹ️ Обычное уведомление (без будильника)")
        }
    }
    
    override fun onNewToken(token: String) {
        Log.d("FCM", "🔑 Новый FCM токен: $token")
        
        // Сохраняем токен в SharedPreferences
        val prefs = getSharedPreferences("VisaBotAlarm", MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
        
        // Подписываемся на топик "visa_alarm"
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
            .subscribeToTopic("visa_alarm")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "✅ Успешно подписались на топик visa_alarm")
                } else {
                    Log.e("FCM", "❌ Ошибка подписки на топик: ${task.exception}")
                }
            }
    }
}
