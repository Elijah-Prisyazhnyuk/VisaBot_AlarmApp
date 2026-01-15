class AlarmService {
    companion object {
        fun triggerAlarm(context: Context, message: String) {
            // Запускаем AlarmActivity
            val intent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("message", message)
            }
            context.startActivity(intent)
            
            // Отправляем уведомление
            sendUrgentNotification(context, message)
        }
        
        private fun sendUrgentNotification(context: Context, message: String) {
            val intent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("message", message)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(context, "visa_bot_urgent")
                .setContentTitle("🚨 СРОЧНО! МЕСТА ПОЯВИЛИСЬ!")
                .setContentText(message.take(100))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .build()
            
            NotificationManagerCompat.from(context).notify(999, notification)
        }
    }
}
