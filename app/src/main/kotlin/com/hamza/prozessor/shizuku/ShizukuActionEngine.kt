package com.hamza.prozessor.shizuku

import android.util.Log
import com.hamza.prozessor.data.OffenderDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ShizukuActionEngine"

/**
 * Shizuku UserService üzerinden privileged eylemleri gerçekleştirir.
 *
 * Her eylem:
 * 1. İnsan okunabilir açıklama döndürür (UI'da gösterilmek üzere)
 * 2. Başarı/başarısızlık sonucu döndürür
 * 3. Log'a kaydedilir
 *
 * Eğer Shizuku bağlı değilse, eşdeğer ADB komutunu döndürür (kopyalanabilir).
 */
object ShizukuActionEngine {

    /**
     * Eylem sonucu sarmalayıcı.
     *
     * [basarili]: eylem başarıyla tamamlandı mı
     * [mesaj]: kullanıcıya gösterilecek mesaj
     * [adbKomutEsdegeri]: Shizuku yoksa ADB ile nasıl yapılır
     * [binderCagrisi]: Shizuku'nun arka planda ne çağırdığı (öğrenme amaçlı)
     */
    data class EylemSonucu(
        val basarili: Boolean,
        val mesaj: String,
        val adbKomutEsdegeri: String,
        val binderCagrisi: String = ""
    )

    // ─── Temel Eylemler ───────────────────────────────────────────────────────

    /**
     * Uygulamayı zorla durdur.
     * Eşdeğer ADB: adb shell am force-stop <package>
     */
    suspend fun zorlaKapat(paketAdi: String): EylemSonucu = withContext(Dispatchers.IO) {
        val adbKomut = "adb shell am force-stop $paketAdi"
        val binderCagrisi = "IActivityManager.forceStopPackage(\"$paketAdi\", 0)"

        val sonuc = ShizukuManager.withService { servis ->
            servis.forceStopPackage(paketAdi)
        }

        when {
            sonuc == null -> EylemSonucu(
                basarili = false,
                mesaj = "Shizuku bağlı değil",
                adbKomutEsdegeri = adbKomut,
                binderCagrisi = binderCagrisi
            )
            sonuc -> {
                Log.i(TAG, "Zorla kapatıldı: $paketAdi")
                EylemSonucu(
                    basarili = true,
                    mesaj = "$paketAdi durduruldu",
                    adbKomutEsdegeri = adbKomut,
                    binderCagrisi = binderCagrisi
                )
            }
            else -> EylemSonucu(
                basarili = false,
                mesaj = "Durdurma başarısız — sistem uygulaması olabilir",
                adbKomutEsdegeri = adbKomut,
                binderCagrisi = binderCagrisi
            )
        }
    }

    /**
     * Arka plan çalışmasını kısıtla.
     * Eşdeğer ADB: adb shell cmd appops set <package> RUN_IN_BACKGROUND deny
     */
    suspend fun arkaPlaniKisitla(paketAdi: String): EylemSonucu = withContext(Dispatchers.IO) {
        val adbKomut = "adb shell cmd appops set $paketAdi RUN_IN_BACKGROUND deny"
        val binderCagrisi = "AppOpsManager.setMode(OP_RUN_IN_BACKGROUND, uid, \"$paketAdi\", MODE_IGNORED)"

        val sonuc = ShizukuManager.withService { servis ->
            servis.restrictBackground(paketAdi)
        }

        when {
            sonuc == null -> EylemSonucu(
                basarili = false,
                mesaj = "Shizuku bağlı değil",
                adbKomutEsdegeri = adbKomut,
                binderCagrisi = binderCagrisi
            )
            sonuc -> {
                Log.i(TAG, "Arka plan kısıtlandı: $paketAdi")
                EylemSonucu(
                    basarili = true,
                    mesaj = "$paketAdi arka plan kısıtlandı",
                    adbKomutEsdegeri = adbKomut,
                    binderCagrisi = binderCagrisi
                )
            }
            else -> EylemSonucu(
                basarili = false,
                mesaj = "Kısıtlama başarısız",
                adbKomutEsdegeri = adbKomut,
                binderCagrisi = binderCagrisi
            )
        }
    }

    /**
     * Overlay (SYSTEM_ALERT_WINDOW) iznini iptal et.
     * Vodafone gibi Visible tier'da yanlış oturan uygulamalar için.
     */
    suspend fun overlayIzniniKaldir(paketAdi: String): EylemSonucu = withContext(Dispatchers.IO) {
        val adbKomut = "adb shell cmd appops set $paketAdi SYSTEM_ALERT_WINDOW deny"
        val binderCagrisi = "AppOpsManager.setMode(OP_SYSTEM_ALERT_WINDOW, uid, \"$paketAdi\", MODE_ERRORED)"

        val sonuc = ShizukuManager.withService { servis ->
            servis.revokeOverlayPermission(paketAdi)
        }

        when {
            sonuc == null -> EylemSonucu(false, "Shizuku bağlı değil", adbKomut, binderCagrisi)
            sonuc -> EylemSonucu(true, "Overlay izni kaldırıldı", adbKomut, binderCagrisi)
            else -> EylemSonucu(false, "İzin kaldırma başarısız", adbKomut, binderCagrisi)
        }
    }

    /**
     * Belirli bir runtime iznini iptal et.
     * Eşdeğer ADB: adb shell pm revoke <package> <permission>
     */
    suspend fun izniIptalEt(paketAdi: String, izin: String): EylemSonucu = withContext(Dispatchers.IO) {
        val izinKisa = izin.substringAfterLast(".")
        val adbKomut = "adb shell pm revoke $paketAdi $izin"
        val binderCagrisi = "PackageManager.revokeRuntimePermission(\"$paketAdi\", \"$izin\", UserHandle.CURRENT)"

        val sonuc = ShizukuManager.withService { servis ->
            servis.revokePermission(paketAdi, izin)
        }

        when {
            sonuc == null -> EylemSonucu(false, "Shizuku bağlı değil", adbKomut, binderCagrisi)
            sonuc -> {
                Log.i(TAG, "İzin iptal edildi: $paketAdi / $izinKisa")
                EylemSonucu(true, "$izinKisa izni kaldırıldı", adbKomut, binderCagrisi)
            }
            else -> EylemSonucu(false, "İzin iptali başarısız (sistem uygulaması?)", adbKomut, binderCagrisi)
        }
    }

    /**
     * Offender veritabanındaki uygulamaya önerilen tüm eylemleri uygula.
     * Tek dokunuşla tam temizleme.
     */
    suspend fun tamTemizle(paketAdi: String): List<EylemSonucu> = withContext(Dispatchers.IO) {
        val sonuclar = mutableListOf<EylemSonucu>()
        val offender = OffenderDatabase.findOffender(paketAdi)

        // 1. Arka planı kısıtla
        sonuclar.add(arkaPlaniKisitla(paketAdi))

        // 2. Overlay iznini kaldır (eğer offender listesindeyse)
        if (offender != null) {
            sonuclar.add(overlayIzniniKaldir(paketAdi))
        }

        // 3. Gereksiz izinleri iptal et (offender listesinde belirtilmişse)
        offender?.permissionsToRevoke?.forEach { izin ->
            sonuclar.add(izniIptalEt(paketAdi, izin))
        }

        // 4. Son olarak zorla kapat
        sonuclar.add(zorlaKapat(paketAdi))

        sonuclar
    }

    /**
     * Arka plan kısıtlamasını geri al (test veya undo için).
     */
    suspend fun arkaPlaniSerbest(paketAdi: String): EylemSonucu = withContext(Dispatchers.IO) {
        val adbKomut = "adb shell cmd appops set $paketAdi RUN_IN_BACKGROUND allow"

        val sonuc = ShizukuManager.withService { servis ->
            servis.allowBackground(paketAdi)
        }

        when {
            sonuc == null -> EylemSonucu(false, "Shizuku bağlı değil", adbKomut)
            sonuc -> EylemSonucu(true, "Arka plan kısıtı kaldırıldı", adbKomut)
            else -> EylemSonucu(false, "İşlem başarısız", adbKomut)
        }
    }
}
