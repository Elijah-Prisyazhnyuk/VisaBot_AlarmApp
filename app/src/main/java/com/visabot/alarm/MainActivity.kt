package com.visabot.alarm

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {
    private val NOTIFICATION_PERMISSION_CODE = 100
    private lateinit var sharedPrefs: android.content.SharedPreferences
    
    private lateinit var botTokenInput: EditText
    private lateinit var keywordInput: EditText
    private lateinit var enabledSwitch: Switch
    private lateinit var statusText: TextView
    private lateinit var testButton: Button
    private lateinit var fcmTokenText: TextView
    
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
        fcmTokenText = findViewById(R.id.fcmTokenText)
        
        // Загрузка сохраненных настроек
        botTokenInput.setText(sharedPrefs.getString("bot_token", "8503440831:AAFl8X6gE8mEkGO1RZuOaxa6wj9aP94op_s"))
        keywordInput.setText(sharedPrefs.getString("keyword", "🚨СРОЧНО🚨"))
        enabledSwitch.isChecked = sharedPrefs.getBoolean("enabled", true)
        
        // Отображение FCM токена
        val fcmToken = sharedPrefs.getString("fcm_token", "Получение токена...")
        fcmTokenText.text = "FCM Token: ${fcmToken?.take(20)}..."
        
        // Создание канала уведомлений
        createNotificationChannel()
        
        // Запрос разрешений
        requestNotificationPermission()
        
        // Инициализация Firebase и подписка на топик
        initializeFirebase()
        
        // Кнопка сохранения
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveSettings()
        }
        
        // Кнопка теста
        testButton.setOnClickListener {
            testAlarm()
        }
        
        // Запуск фонового сервиса
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
        
        updateStatus("Готов к работе (Firebase + Telegram)")
    }
    
    private fun initializeFirebase() {
        // Получаем FCM токен
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                sharedPrefs.edit().putString("fcm_token", token).apply()
                fcmTokenText.text = "FCM Token: ${token.take(20)}..."
                Toast.makeText(this, "FCM токен получен", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Ошибка получения FCM токена", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Подписка на топик
        FirebaseMessaging.getInstance().subscribeToTopic("visa_alarm")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "✅ Подписка на visa_alarm активна", Toast.LENGTH_SHORT).show()
                    updateStatus("Firebase подключен к топику visa_alarm")
                } else {
                    Toast.makeText(this, "❌ Ошибка подписки на топик", Toast.LENGTH_SHORT).show()
                }
            }
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
        updateStatus("Мониторинг запущен (Firebase + Telegram)")
    }
    
    private fun stopMonitoringService() {
        val intent = Intent(this, MonitoringService::class.java)
        stopService(intent)
        updateStatus("Мониторинг остановлен")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Канал для обычных уведомлений
            val channel = NotificationChannel(
                "visa_bot_service",
                "VisaBot Сервис",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновый мониторинг сообщений"
            }
            
            // Канал для СРОЧНЫХ уведомлений
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
        
        // Разрешение на игнорирование оптимизации батареи
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
