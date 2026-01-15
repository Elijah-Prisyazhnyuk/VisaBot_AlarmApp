package com.visabot.alarm

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL

class MainActivity : AppCompatActivity() {
    private val NOTIFICATION_PERMISSION_CODE = 100
    private lateinit var sharedPrefs: android.content.SharedPreferences
    
    private lateinit var botTokenInput: EditText
    private lateinit var keywordInput: EditText
    private lateinit var enabledSwitch: Switch
    private lateinit var statusText: TextView
    private lateinit var testButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        sharedPrefs = getSharedPreferences("VisaBotAlarm", Context.MODE_PRIVATE)
        
        // Инициализация UI
        botTokenInput = findViewById(R.id.botTokenInput)
        keywordInput = findViewById(R.id.keywordInput)
        enabledSwitch = findViewById(R.id.enabledSwitch)
        statusText = findViewById(R.id.statusText)
        testButton = findViewById(R.id.testButton)
        
        // Загрузка сохраненных настроек
        botTokenInput.setText(sharedPrefs.getString("bot_token", "8503440831:AAFl8X6gE8mEkGO1RZuOaxa6wj9aP94op_s"))
        keywordInput.setText(sharedPrefs.getString("keyword", "🚨СРОЧНО🚨"))
        enabledSwitch.isChecked = sharedPrefs.getBoolean("enabled", true)
        
        createNotificationChannel()
        requestNotificationPermission()
        
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveSettings()
        }
        
        testButton.setOnClickListener {
            testAlarm()
        }
        
        // === НОВАЯ ФУНКЦИЯ ДЛЯ ДЕБАГА ===
        // Долгое нажатие на кнопку "Тест" покажет последние сообщения
        testButton.setOnLongClickListener {
            debugCheckMessages()
            true // Возвращаем true, чтобы обычный клик не сработал
        }
        
        if (enabledSwitch.isChecked) {
            startMonitoringService()
        }
        
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startMonitoringService()
            } else {
                stopMonitoringService()
            }
        }
        
        updateStatus("Готов к работе. (Удерживайте 'Тест' для проверки сообщений)")
    }
    
    // === ФУНКЦИЯ ДЛЯ ПРОВЕРКИ СООБЩЕНИЙ ===
    private fun debugCheckMessages() {
        val token = botTokenInput.text.toString()
        val keyword = keywordInput.text.toString()
        
        updateStatus("⏳ Подключение к Telegram...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Запрос последних обновлений БЕЗ смещения, чтобы увидеть хоть что-то
                val url = "https://api.telegram.org/bot$token/getUpdates?limit=5"
                val response = URL(url).readText()
                val json = JSONObject(response)
                
                val sb = StringBuilder()
                sb.append("🔍 РЕЗУЛЬТАТ ПРОВЕРКИ:\n\n")
                
                if (json.optBoolean("ok")) {
                    val result = json.getJSONArray("result")
                    sb.append("Найдено обновлений: ${result.length()}\n\n")
                    
                    if (result.length() == 0) {
                        sb.append("⚠️ Список пуст! Возможные причины:\n")
                        sb.append("1. Webhook включен (getUpdates не работает)\n")
                        sb.append("2. Нет новых сообщений за 24ч\n")
                        sb.append("3. Другой бот уже прочитал их")
                    }
                    
                    for (i in 0 until result.length()) {
                        val item = result.getJSONObject(i)
                        val updateId = item.optLong("update_id")
                        val message = item.optJSONObject("message")
                        val text = message?.optString("text") ?: "No text"
                        val chat = message?.optJSONObject("chat")
                        val chatId = chat?.optLong("id") ?: 0
                        
                        sb.append("[$i] ID: $updateId | ChatID: $chatId\n")
                        sb.append("Текст: '$text'\n")
                        
                        if (text.contains(keyword, ignoreCase = false)) {
                            sb.append("✅ СЛОВО НАЙДЕНО!\n")
                        } else {
                            sb.append("❌ Нет ключевого слова\n")
                        }
                        sb.append("----------------\n")
                    }
                } else {
                    sb.append("Ошибка API Telegram:\n$response")
                }
                
                withContext(Dispatchers.Main) {
                    showDebugDialog(sb.toString())
                    statusText.text = "Проверка завершена"
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showDebugDialog("Ошибка сети: ${e.message}\n\nЕсли ошибка 'Conflict', значит нужно отключить Webhook.")
                    statusText.text = "Ошибка проверки"
                }
            }
        }
    }
    
    private fun showDebugDialog(text: String) {
        AlertDialog.Builder(this)
            .setTitle("Debug Info")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun saveSettings() {
        val editor = sharedPrefs.edit()
        editor.putString("bot_token", botTokenInput.text.toString())
        editor.putString("keyword", keywordInput.text.toString())
        editor.putBoolean("enabled", enabledSwitch.isChecked)
        editor.apply()
        
        Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
        
        if (enabledSwitch.isChecked) {
            startMonitoringService()
        }
    }
    
    private fun testAlarm() {
        AlarmService.triggerAlarm(this, "Тестовый будильник! Проверка работы приложения.")
        updateStatus("Тестовый будильник запущен")
    }
    
    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = "Статус: $message"
        }
    }
    
    private fun startMonitoringService() {
        val intent = Intent(this, MonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatus("Мониторинг запущен")
    }
    
    private fun stopMonitoringService() {
        val intent = Intent(this, MonitoringService::class.java)
        stopService(intent)
        updateStatus("Мониторинг остановлен")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "visa_bot_service",
                "VisaBot Сервис",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновый мониторинг сообщений"
            }
            
            val urgentChannel = NotificationChannel(
                "visa_bot_urgent",
                "СРОЧНЫЕ УВЕДОМЛЕНИЯ",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Критические уведомления о визе"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(urgentChannel)
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }
}
