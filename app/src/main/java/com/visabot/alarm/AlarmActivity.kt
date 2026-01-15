class AlarmActivity : AppCompatActivity() {
    private lateinit var mediaPlayer: android.media.MediaPlayer
    private lateinit var vibrator: android.os.Vibrator
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Включаем экран даже если телефон заблокирован
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        
        setContentView(R.layout.activity_alarm)
        
        val message = intent.getStringExtra("message") ?: "МЕСТА ПОЯВИЛИСЬ!"
        findViewById<TextView>(R.id.alarmMessage).text = message
        
        // Запуск звука будильника
        startAlarmSound()
        
        // Запуск вибрации
        startVibration()
        
        // Кнопка выключения
        findViewById<Button>(R.id.stopAlarmButton).setOnClickListener {
            stopAlarm()
        }
    }
    
    private fun startAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                isLooping = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun startVibration() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                android.os.VibrationEffect.createWaveform(pattern, 0)
            )
        } else {
            vibrator.vibrate(pattern, 0)
        }
    }
    
    private fun stopAlarm() {
        if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
        
        if (::vibrator.isInitialized) {
            vibrator.cancel()
        }
        
        // Отменяем уведомление
        NotificationManagerCompat.from(this).cancel(999)
        
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}
