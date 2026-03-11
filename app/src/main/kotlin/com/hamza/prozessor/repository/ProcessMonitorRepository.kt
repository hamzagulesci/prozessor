package com.hamza.prozessor.repository

import android.util.Log
import com.hamza.prozessor.data.*
import com.hamza.prozessor.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

private const val TAG = "ProcessMonitorRepo"

/**
 * Process ve bellek verilerini Shizuku UserService üzerinden toplar ve parse eder.
 *
 * Veri kaynakları:
 * - dumpsys meminfo --oom → OOM tier + RSS/PSS değerleri
 * - top -n 1 -b          → Anlık CPU yüzdesi + toplam CPU zamanı
 * - /proc/meminfo        → Swap + MemAvailable değerleri
 *
 * Gerçek dump formatı (curtana / crDroid 12.8) temel alınarak yazıldı.
 */
class ProcessMonitorRepository {

    /**
     * Her [yenilemeAraligi] milisaniyede bir process listesini günceller.
     * Flow olarak emit eder — ViewModel bu flow'u collect eder.
     */
    fun processListesiFlow(yenilemeAraligi: Long = 5000L): Flow<List<ProcessInfo>> = flow {
        while (true) {
            try {
                val liste = processListesiniYukle()
                emit(liste)
            } catch (e: Exception) {
                Log.e(TAG, "Process listesi yüklenirken hata: ${e.message}")
            }
            delay(yenilemeAraligi)
        }
    }

    /**
     * Her [yenilemeAraligi] milisaniyede bir bellek bilgisini günceller.
     */
    fun belleKBilgisiFlow(yenilemeAraligi: Long = 5000L): Flow<MemoryInfo?> = flow {
        while (true) {
            try {
                val bellek = bellekBilgisiniYukle()
                emit(bellek)
            } catch (e: Exception) {
                Log.e(TAG, "Bellek bilgisi yüklenirken hata: ${e.message}")
            }
            delay(yenilemeAraligi)
        }
    }

    // ─── Veri Yükleme ─────────────────────────────────────────────────────────

    private suspend fun processListesiniYukle(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val meminfoMetni = ShizukuManager.withService { it.getDumpsysMeminfo() } ?: return@withContext emptyList()
        val topMetni = ShizukuManager.withService { it.getTopOutput() } ?: ""

        // CPU verilerini top çıktısından çek (PID → CPU bilgisi haritası)
        val cpuHaritasi = topCiktisinuParse(topMetni)

        // PSS bölümünü parse et (daha doğru bellek değerleri)
        parsePssBolumuOnce(meminfoMetni, cpuHaritasi)
    }

    private suspend fun bellekBilgisiniYukle(): MemoryInfo? = withContext(Dispatchers.IO) {
        val meminfoMetni = ShizukuManager.withService { it.getDumpsysMeminfo() } ?: return@withContext null
        parseBellekBilgisi(meminfoMetni)
    }

    // ─── Dumpsys Meminfo Parser ────────────────────────────────────────────────

    /**
     * "Total PSS by OOM adjustment" bölümünü parse eder.
     *
     * Beklenen format (gerçek dump'tan):
     *   "    697,827K: com.zhiliaoapp.musically (pid 27629)"
     *   "    375,309K: system (pid 1811)"
     *   "  1,315,542K: Cached"       ← tier başlığı
     */
    private fun parsePssBolumuOnce(
        metin: String,
        cpuHaritasi: Map<Int, CpuBilgisi>
    ): List<ProcessInfo> {
        val sonuclar = mutableListOf<ProcessInfo>()

        // "Total PSS by OOM adjustment" bölümünü bul
        val pssBolumuBaslangic = metin.indexOf("Total PSS by OOM adjustment:")
        if (pssBolumuBaslangic == -1) {
            Log.w(TAG, "PSS bölümü bulunamadı, RSS bölümünü dene")
            return parseRssBolumu(metin, cpuHaritasi)
        }

        val pssBolumu = metin.substring(pssBolumuBaslangic)
        val satirlar = pssBolumu.lines()

        var mevcutTier = OomTier.UNKNOWN

        // Tier başlığı pattern: "    637,447K: Persistent" (K: sonrası harf)
        // Process satırı pattern: "        375,309K: system (pid 1811)"
        val tierBaslikPattern = Regex("""^\s{4}([\d,]+)K:\s+([A-Z][A-Za-z\s]+)$""")
        val processSatiriPattern = Regex("""^\s{8}([\d,]+)K:\s+([\w.\-:]+)\s+\(pid\s+(\d+)""")
        // Native servis pattern (process adı, paket adı değil)
        val nativeSatirPattern = Regex("""^\s{8,}([\d,]+)K:\s+([\w.\-@]+)\s+\(pid\s+(\d+)""")

        for (satir in satirlar) {
            // Tier başlığını tespit et
            val tierEslesmesi = tierBaslikPattern.find(satir)
            if (tierEslesmesi != null) {
                mevcutTier = OomTier.fromDumpsysHeader(tierEslesmesi.groupValues[2])
                continue
            }

            // Process satırını parse et
            val processEslesmesi = processSatiriPattern.find(satir)
                ?: nativeSatirPattern.find(satir)

            if (processEslesmesi != null) {
                val pssKb = processEslesmesi.groupValues[1].replace(",", "").toLongOrNull() ?: continue
                val isim = processEslesmesi.groupValues[2]
                val pid = processEslesmesi.groupValues[3].toIntOrNull() ?: continue

                val cpuBilgisi = cpuHaritasi[pid]
                val offenderGirisi = OffenderDatabase.findOffender(isim)

                sonuclar.add(
                    ProcessInfo(
                        pid = pid,
                        packageName = isim,
                        displayName = paketAdiniKisalt(isim),
                        rssKb = pssKb, // PSS bölümünde RSS doldurulmuyor, PSS kullanılıyor
                        pssKb = pssKb,
                        oomTier = mevcutTier,
                        cpuPercent = cpuBilgisi?.cpuYuzdesi ?: 0f,
                        cpuTimeFormatted = cpuBilgisi?.cpuZamani ?: "",
                        uid = cpuBilgisi?.uid ?: "",
                        isSystemProcess = mevcutTier == OomTier.NATIVE ||
                                mevcutTier == OomTier.PERSISTENT ||
                                isim == "system" ||
                                isim.startsWith("android.") ||
                                isim.startsWith("com.android."),
                        isKnownOffender = offenderGirisi != null,
                        offenderReason = offenderGirisi?.reason ?: ""
                    )
                )
            }
        }

        return sonuclar.sortedByDescending { it.pssKb }
    }

    /**
     * PSS bölümü yoksa RSS bölümünü fallback olarak parse et.
     * Format aynı, sadece "Total RSS by OOM adjustment" başlığı farklı.
     */
    private fun parseRssBolumu(
        metin: String,
        cpuHaritasi: Map<Int, CpuBilgisi>
    ): List<ProcessInfo> {
        // RSS bölümü için aynı mantık, sadece başlangıç noktası farklı
        val rssBolumuBaslangic = metin.indexOf("Total RSS by OOM adjustment:")
        if (rssBolumuBaslangic == -1) return emptyList()

        // PSS parse ile aynı — sadece metin referansı değişiyor
        return parsePssBolumuOnce(
            metin.substring(rssBolumuBaslangic).replaceFirst(
                "Total RSS by OOM adjustment:",
                "Total PSS by OOM adjustment:"
            ),
            cpuHaritasi
        )
    }

    /**
     * Toplam bellek satırlarını parse et.
     *
     * Beklenen format (gerçek dump'tan):
     *   "Total RAM: 5,739,564K (status normal)"
     *   "Free RAM: 2,122,282K (1,315,542K cached pss + 580,992K cached kernel + 225,748K free)"
     *   "Used RAM: 4,133,801K (3,379,713K used pss + 754,088K kernel)"
     *   "ZRAM:   313,576K physical used for 1,123,812K in swap (3,145,724K total swap)"
     */
    private fun parseBellekBilgisi(metin: String): MemoryInfo? {
        try {
            fun satirSayisiniBul(satirlar: List<String>, anahtar: String): Long {
                val satir = satirlar.firstOrNull { it.trimStart().startsWith(anahtar) } ?: return 0L
                val sayi = Regex("""([\d,]+)K""").find(satir)?.groupValues?.get(1) ?: return 0L
                return sayi.replace(",", "").toLong()
            }

            val satirlar = metin.lines()

            // /proc/meminfo bölümünü bul (---SWAP--- ile ayrılmış)
            val swapBolumu = metin.substringAfter("---SWAP---", "")
            val swapSatirlari = swapBolumu.lines()

            fun swapDegerBul(anahtar: String): Long {
                val satir = swapSatirlari.firstOrNull { it.startsWith(anahtar) } ?: return 0L
                return Regex("""(\d+)""").find(satir)?.groupValues?.get(1)?.toLong() ?: 0L
            }

            val toplamKb = satirSayisiniBul(satirlar, "Total RAM:")
            val bosKb = satirSayisiniBul(satirlar, "Free RAM:")

            // Used PSS (Free RAM satırının ilk değeri cached pss)
            val usedPssKb = run {
                val satir = satirlar.firstOrNull { it.trimStart().startsWith("Used RAM:") } ?: return@run 0L
                val eslesme = Regex("""([\d,]+)K used pss""").find(satir)
                eslesme?.groupValues?.get(1)?.replace(",", "")?.toLong() ?: 0L
            }

            val cachedPssKb = run {
                val satir = satirlar.firstOrNull { it.trimStart().startsWith("Free RAM:") } ?: return@run 0L
                val eslesme = Regex("""([\d,]+)K cached pss""").find(satir)
                eslesme?.groupValues?.get(1)?.replace(",", "")?.toLong() ?: 0L
            }

            // ZRAM değerleri
            // Format: "ZRAM:   313,576K physical used for 1,123,812K in swap (3,145,724K total swap)"
            val zramSatiri = satirlar.firstOrNull { it.trimStart().startsWith("ZRAM:") }
            var zramFiziksel = 0L
            var zramSwap = 0L
            var zramToplam = 0L
            if (zramSatiri != null) {
                val zramEslesme = Regex(
                    """([\d,]+)K physical used for ([\d,]+)K in swap \(([\d,]+)K total"""
                ).find(zramSatiri)
                if (zramEslesme != null) {
                    zramFiziksel = zramEslesme.groupValues[1].replace(",", "").toLong()
                    zramSwap = zramEslesme.groupValues[2].replace(",", "").toLong()
                    zramToplam = zramEslesme.groupValues[3].replace(",", "").toLong()
                }
            }

            // MemAvailable: /proc/meminfo bölümünden al
            val memAvailable = swapDegerBul("MemAvailable:")
            val swapFree = swapDegerBul("SwapFree:")

            return MemoryInfo(
                totalKb = toplamKb,
                freeKb = bosKb,
                availableKb = if (memAvailable > 0) memAvailable else bosKb,
                usedPssKb = usedPssKb,
                cachedPssKb = cachedPssKb,
                zramPhysicalKb = zramFiziksel,
                zramSwapUsedKb = zramSwap,
                zramTotalKb = zramToplam,
                swapFreeKb = swapFree
            )
        } catch (e: Exception) {
            Log.e(TAG, "Bellek parse hatası: ${e.message}")
            return null
        }
    }

    // ─── Top Çıktısı Parser ───────────────────────────────────────────────────

    data class CpuBilgisi(
        val cpuYuzdesi: Float,
        val cpuZamani: String,
        val uid: String
    )

    /**
     * top -n 1 -b çıktısını parse eder.
     *
     * Beklenen format (gerçek dump'tan):
     *   "27629 u0_a364  1 -19  41G 638M 327M S  3.5  11.3   4:05.69 com.zhiliaoapp.musically"
     *   "  PID USER     PR  NI  VIRT  RES  SHR S [%CPU] %MEM    TIME+ COMMAND"
     */
    private fun topCiktisinuParse(metin: String): Map<Int, CpuBilgisi> {
        val harita = mutableMapOf<Int, CpuBilgisi>()
        val topBolumu = metin.substringAfter("---CPU---", metin)

        // Başlık satırını atla, process satırlarını işle
        for (satir in topBolumu.lines()) {
            val parcalar = satir.trim().split(Regex("\\s+"))
            if (parcalar.size < 12) continue

            val pid = parcalar[0].toIntOrNull() ?: continue
            val uid = parcalar[1]
            // %CPU değeri: köşeli parantez içinde veya düz sayı
            val cpuStr = parcalar[8].removeSurrounding("[", "]")
            val cpuYuzdesi = cpuStr.toFloatOrNull() ?: continue
            val cpuZamani = parcalar[10]  // "4:05.69" formatı

            harita[pid] = CpuBilgisi(cpuYuzdesi, cpuZamani, uid)
        }

        return harita
    }

    // ─── Yardımcı Fonksiyonlar ────────────────────────────────────────────────

    /**
     * Uzun paket adını kısaltır.
     * "com.zhiliaoapp.musically" → "TikTok (musically)"
     * "com.vodafone.selfservis"  → "selfservis"
     */
    private fun paketAdiniKisalt(paketAdi: String): String {
        val offender = OffenderDatabase.findOffender(paketAdi)
        if (offender != null) return offender.displayName

        return when {
            paketAdi == "system" -> "System Server"
            paketAdi == "com.android.systemui" -> "SystemUI"
            paketAdi.contains(":") -> paketAdi.substringAfterLast(":").let { son ->
                "${paketAdi.substringBefore(":")}:$son"
            }
            else -> paketAdi.substringAfterLast(".")
        }
    }
}
