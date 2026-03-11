package com.hamza.prozessor.shizuku

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.hamza.prozessor.IUserService

/**
 * Shizuku UserService implementasyonu.
 *
 * Bu sınıf Shizuku'nun shell (uid=2000) bağlamında ayrı bir process olarak çalışır.
 * Ana uygulama process'inden AIDL üzerinden çağrılır.
 *
 * Shizuku.bindUserService() ile başlatılır — Android servis lifecycle'ından bağımsız.
 */
class UserService : IUserService.Stub() {

    companion object {
        private const val SERVIS_VERSIYONU = 1
    }

    /**
     * Shell komutu çalıştırır ve çıktısını string olarak döndürür.
     * Shizuku bağlamında çalıştığından shell (uid=2000) yetkileriyle çalışır.
     */
    private fun komutCalistir(vararg args: String): String {
        return try {
            val process = Runtime.getRuntime().exec(args)
            val cikti = process.inputStream.bufferedReader().readText()
            val hata = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (hata.isNotEmpty() && cikti.isEmpty()) hata else cikti
        } catch (e: Exception) {
            "HATA: ${e.message}"
        }
    }

    // ─── Bilgi okuma ──────────────────────────────────────────────────────────

    override fun getDumpsysMeminfo(): String =
        komutCalistir("dumpsys", "meminfo", "--oom")

    override fun getTopOutput(): String =
        komutCalistir("top", "-n", "1", "-b")

    override fun getAlarmInfo(packageFilter: String): String {
        val tamCikti = komutCalistir("dumpsys", "alarm")
        // Belirtilen paket adına göre filtrele
        return tamCikti.lines()
            .filter { satir ->
                packageFilter.isEmpty() || satir.contains(packageFilter, ignoreCase = true)
            }
            .joinToString("\n")
    }

    override fun getBatteryStats(packageName: String): String {
        val tamCikti = komutCalistir("dumpsys", "batterystats", "--charged", packageName)
        return tamCikti.take(8000) // Çok uzun olabilir, kırp
    }

    // ─── Eylemler ─────────────────────────────────────────────────────────────

    override fun forceStopPackage(packageName: String): Boolean {
        // am force-stop komutu başarılıysa çıktı boş gelir
        val cikti = komutCalistir("am", "force-stop", packageName)
        return !cikti.contains("Error", ignoreCase = true) &&
               !cikti.contains("Exception", ignoreCase = true)
    }

    override fun restrictBackground(packageName: String): Boolean {
        // Android 8+ için cmd appops kullan
        val cikti = komutCalistir(
            "cmd", "appops", "set", packageName, "RUN_IN_BACKGROUND", "deny"
        )
        return !cikti.contains("Error", ignoreCase = true)
    }

    override fun allowBackground(packageName: String): Boolean {
        val cikti = komutCalistir(
            "cmd", "appops", "set", packageName, "RUN_IN_BACKGROUND", "allow"
        )
        return !cikti.contains("Error", ignoreCase = true)
    }

    override fun revokePermission(packageName: String, permission: String): Boolean {
        val cikti = komutCalistir("pm", "revoke", packageName, permission)
        return !cikti.contains("Error", ignoreCase = true) &&
               !cikti.contains("Exception", ignoreCase = true)
    }

    override fun grantPermission(packageName: String, permission: String): Boolean {
        val cikti = komutCalistir("pm", "grant", packageName, permission)
        return !cikti.contains("Error", ignoreCase = true)
    }

    override fun revokeOverlayPermission(packageName: String): Boolean {
        // Visible tier'da yanlış oturan uygulamalar için overlay iznini kaldır
        val cikti = komutCalistir(
            "cmd", "appops", "set", packageName, "SYSTEM_ALERT_WINDOW", "deny"
        )
        return !cikti.contains("Error", ignoreCase = true)
    }

    // ─── Servis kontrolü ──────────────────────────────────────────────────────

    override fun getVersion(): Int = SERVIS_VERSIYONU

    override fun destroy() {
        // Kaynakları temizle ve process'i kapat
        System.exit(0)
    }
}

/**
 * Shizuku UserService'in bağlanması için gerekli boş Service sınıfı.
 * Shizuku bu sınıfı kullanarak UserService process'ini başlatır.
 * onBind() IUserService stub'ını döndürmelidir.
 */
class UserServiceWrapper : Service() {
    private val userService = UserService()

    override fun onBind(intent: Intent?): IBinder = userService
}
