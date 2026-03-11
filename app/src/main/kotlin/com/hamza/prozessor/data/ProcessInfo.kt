package com.hamza.prozessor.data

import kotlinx.serialization.Serializable

/**
 * Tek bir çalışan sürecin anlık bilgilerini tutar.
 * dumpsys meminfo --oom çıktısından parse edilir.
 */
@Serializable
data class ProcessInfo(
    val pid: Int,
    val packageName: String,
    val displayName: String,          // Kullanıcıya gösterilecek kısa isim
    val rssKb: Long,                  // Resident Set Size (toplam fiziksel RAM)
    val pssKb: Long,                  // Proportional Set Size (paylaşılan hafıza düzeltilmiş)
    val oomTier: OomTier,             // Android OOM öncelik katmanı
    val cpuPercent: Float = 0f,       // top çıktısından anlık CPU yüzdesi
    val cpuTimeFormatted: String = "", // "4:05.69" formatında toplam CPU zamanı
    val uid: String = "",             // u0_a364 gibi kullanıcı ID'si
    val isSystemProcess: Boolean = false,
    val isKnownOffender: Boolean = false,  // Offender listesinde var mı
    val offenderReason: String = "",       // Neden offender olduğunun açıklaması
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Android'in OOM killer öncelik sıralaması.
 * Düşük sayı = daha önemli, daha geç öldürülür.
 */
enum class OomTier(val displayName: String, val priority: Int) {
    NATIVE("Native Servis", 0),
    PERSISTENT("Kalıcı", 1),
    PERSISTENT_SERVICE("Kalıcı Servis", 2),
    FOREGROUND("Ön Plan", 3),
    VISIBLE("Görünür", 4),
    PERCEPTIBLE("Algılanabilir", 5),
    B_SERVICE("Arka Servis", 6),
    CACHED("Önbellek", 7),
    UNKNOWN("Bilinmiyor", 99);

    companion object {
        /** dumpsys meminfo çıktısındaki başlık stringinden OomTier çevir */
        fun fromDumpsysHeader(header: String): OomTier = when {
            header.contains("Native", ignoreCase = true) -> NATIVE
            header.contains("Persistent Service", ignoreCase = true) -> PERSISTENT_SERVICE
            header.contains("Persistent", ignoreCase = true) -> PERSISTENT
            header.contains("Foreground", ignoreCase = true) -> FOREGROUND
            header.contains("Visible", ignoreCase = true) -> VISIBLE
            header.contains("Perceptible", ignoreCase = true) -> PERCEPTIBLE
            header.contains("B Service", ignoreCase = true) -> B_SERVICE
            header.contains("Cached", ignoreCase = true) -> CACHED
            else -> UNKNOWN
        }
    }
}

/**
 * Sistemin toplam bellek durumunu özetler.
 * /proc/meminfo + dumpsys meminfo çıktısından doldurulur.
 */
@Serializable
data class MemoryInfo(
    val totalKb: Long,
    val freeKb: Long,
    val availableKb: Long,
    val usedPssKb: Long,
    val cachedPssKb: Long,
    val zramPhysicalKb: Long,    // ZRAM'ın fiziksel RAM tüketimi
    val zramSwapUsedKb: Long,    // ZRAM içindeki swap kullanımı
    val zramTotalKb: Long,
    val swapFreeKb: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Kullanım yüzdesi (0.0 - 1.0) */
    val usageRatio: Float
        get() = if (totalKb > 0) (totalKb - availableKb).toFloat() / totalKb else 0f

    /** Swap doluluk yüzdesi */
    val swapUsageRatio: Float
        get() = if (zramTotalKb > 0) zramSwapUsedKb.toFloat() / zramTotalKb else 0f

    /** Baskı seviyesi için enum */
    val pressureLevel: PressureLevel
        get() = when {
            usageRatio > 0.85f -> PressureLevel.CRITICAL
            usageRatio > 0.70f -> PressureLevel.WARNING
            else -> PressureLevel.NORMAL
        }
}

enum class PressureLevel { NORMAL, WARNING, CRITICAL }

/**
 * Kullanıcı arayüzünde gösterilecek uyarı/alarm kaydı.
 */
@Serializable
data class AlertEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val alertType: AlertType,
    val details: String,
    val actionTaken: String = ""
)

enum class AlertType {
    HIGH_RAM,           // RAM > 200MB
    HIGH_CPU,           // CPU zamanı > 1 saat
    KNOWN_OFFENDER,     // Bilinen saldırgan uygulama
    SYSTEM_SERVER_SPIKE,// system_server CPU spike
    WRONG_OOM_TIER      // Yanlış OOM katmanında oturan uygulama
}
