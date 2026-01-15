class MonitoringService : Service() {
    private var isRunning = false
    private lateinit var sharedPrefs: android.content.SharedPreferences
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        sharedPrefs = getSharedPreferences("VisaBotAlarm", Context.MODE_PRIVATE)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startForeground(1, createServiceNotification())
            startMonitoring()
        }
        return START_STICKY
    }
    
    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, "visa_bot_service")
            .setContentTitle("VisaBot Alarm")
            .setContentText("Мониторинг сообщений активен")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startMonitoring() {
        scope.launch {
            var lastUpdateId = 0L
            
            while (isRunning) {
                try {
                    val botToken = sharedPrefs.getString("bot_token", "") ?: ""
                    val keyword = sharedPrefs.getString("keyword", "🚨СРОЧНО🚨") ?: "🚨СРОЧНО🚨"
                    
                    if (botToken.isNotEmpty()) {
                        val updates = fetchTelegramUpdates(botToken, lastUpdateId)
                        
                        for (update in updates) {
                            val updateId = update.optLong("update_id", 0)
                            if (updateId > lastUpdateId) {
                                lastUpdateId = updateId
                            }
                            
                            val message = update.optJSONObject("message")
                            val text = message?.optString("text", "") ?: ""
                            
                            // Проверяем наличие ключевого слова
                            if (text.contains(keyword, ignoreCase = false)) {
                                // НАЙДЕНО! Запускаем будильник
                                AlarmService.triggerAlarm(applicationContext, text)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // Проверяем каждые 3 секунды
                delay(3000)
            }
        }
    }
    
    private fun fetchTelegramUpdates(botToken: String, offset: Long): List<JSONObject> {
        return try {
            val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=${offset + 1}&timeout=5"
            val response = URL(url).readText()
            val json = JSONObject(response)
            
            if (json.optBoolean("ok", false)) {
                val resultArray = json.optJSONArray("result")
                val list = mutableListOf<JSONObject>()
                
                if (resultArray != null) {
                    for (i in 0 until resultArray.length()) {
                        list.add(resultArray.getJSONObject(i))
                    }
                }
                list
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        scope.cancel()
    }
    
    override fun onBind(intent: Intent?): android.os.IBinder? = null
}
