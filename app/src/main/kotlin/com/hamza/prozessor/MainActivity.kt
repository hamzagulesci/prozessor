package com.hamza.prozessor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import com.hamza.prozessor.shizuku.ShizukuManager
import com.hamza.prozessor.ui.screens.MainScreen
import com.hamza.prozessor.ui.theme.ProzessorTema

/**
 * Ana aktivite — Shizuku yaşam döngüsünü ve Compose UI'ı başlatır.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Shizuku dinleyicilerini başlat
        ShizukuManager.baslat()

        setContent {
            ProzessorTema {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Dinleyicileri temizle — UserService arka planda çalışmaya devam eder
        ShizukuManager.durdur()
    }
}

// ─── Boot Receiver ────────────────────────────────────────────────────────────

/**
 * Cihaz yeniden başladığında Shizuku bağlantısını yeniden kurmaya çalışır.
 * Not: Shizuku'nun da reboot sonrası elle başlatılması gerekebilir.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Shizuku başlamış olabilir, bağlantıyı dene
            ShizukuManager.baslat()
        }
    }
}

// ─── Monitor Foreground Service ───────────────────────────────────────────────

/**
 * Uygulama arka planda giderken process izlemeye devam eden foreground servisi.
 *
 * Kullanım: Uygulamayı kapattığında da uyarılar gelmeye devam etsin istersen başlat.
 * Şimdilik opsiyonel — MainActivity üzerinden de izleme çalışır.
 */
class MonitorForegroundService : Service() {

    companion object {
        private const val BILDIRIM_KANAL_ID = "prozessor_monitor"
        private const val BILDIRIM_ID = 1001

        fun baslat(context: Context) {
            val intent = Intent(context, MonitorForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun durdur(context: Context) {
            context.stopService(Intent(context, MonitorForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        bildirimKanaliniOlustur()
        startForeground(BILDIRIM_ID, bildirimOlustur("Prozessor aktif — süreç izleniyor"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: sistem tarafından öldürülürse yeniden başlat
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun bildirimKanaliniOlustur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val kanal = NotificationChannel(
                BILDIRIM_KANAL_ID,
                "Prozessor Monitör",
                NotificationManager.IMPORTANCE_LOW  // Sessiz bildirim
            ).apply {
                description = "Süreç izleme servisinin arka plan bildirimi"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(kanal)
        }
    }

    private fun bildirimOlustur(metin: String): Notification {
        return NotificationCompat.Builder(this, BILDIRIM_KANAL_ID)
            .setContentTitle("Prozessor")
            .setContentText(metin)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
