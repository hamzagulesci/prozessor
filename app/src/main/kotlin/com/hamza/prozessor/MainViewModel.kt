package com.hamza.prozessor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamza.prozessor.data.*
import com.hamza.prozessor.repository.ProcessMonitorRepository
import com.hamza.prozessor.shizuku.ShizukuActionEngine
import com.hamza.prozessor.shizuku.ShizukuManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Ana ViewModel — tüm UI durumunu yönetir.
 *
 * Repository'den gelen verileri UI için dönüştürür.
 * Shizuku eylemlerini ShizukuActionEngine'e delege eder.
 */
class MainViewModel : ViewModel() {

    private val repo = ProcessMonitorRepository()

    // ─── UI Durumu ────────────────────────────────────────────────────────────

    data class UiDurum(
        val procesListesi: List<ProcessInfo> = emptyList(),
        val bellekBilgisi: MemoryInfo? = null,
        val yukleniyor: Boolean = true,
        val shizukuBagli: Boolean = false,
        val hata: String? = null,
        val aktifUyarilar: List<AlertEvent> = emptyList(),
        val sonEylemSonucu: String? = null
    )

    private val _uiDurum = MutableStateFlow(UiDurum())
    val uiDurum: StateFlow<UiDurum> = _uiDurum.asStateFlow()

    // Log geçmişi (export için)
    private val _logGecmisi = MutableStateFlow<List<AlertEvent>>(emptyList())
    val logGecmisi: StateFlow<List<AlertEvent>> = _logGecmisi.asStateFlow()

    // Detay sayfası için seçili process
    private val _seciliProcess = MutableStateFlow<ProcessInfo?>(null)
    val seciliProcess: StateFlow<ProcessInfo?> = _seciliProcess.asStateFlow()

    init {
        shizukuDurumunuIzle()
        processVerisiniBaslat()
        bellekVerisiniBaslat()
    }

    // ─── Veri Başlatma ────────────────────────────────────────────────────────

    private fun shizukuDurumunuIzle() {
        viewModelScope.launch {
            ShizukuManager.durum.collect { durum ->
                val bagli = durum is ShizukuManager.ShizukuDurumu.Bagli
                _uiDurum.update { it.copy(shizukuBagli = bagli) }
            }
        }
    }

    private fun processVerisiniBaslat() {
        viewModelScope.launch {
            repo.processListesiFlow(5000L)
                .catch { e -> _uiDurum.update { it.copy(hata = e.message) } }
                .collect { liste ->
                    val uyarilar = uyarilariTespit(liste)
                    _uiDurum.update { durum ->
                        durum.copy(
                            procesListesi = liste,
                            yukleniyor = false,
                            aktifUyarilar = uyarilar
                        )
                    }
                    // Yeni uyarıları log'a ekle
                    if (uyarilar.isNotEmpty()) {
                        _logGecmisi.update { it + uyarilar }
                    }
                }
        }
    }

    private fun bellekVerisiniBaslat() {
        viewModelScope.launch {
            repo.belleKBilgisiFlow(5000L)
                .catch { e -> _uiDurum.update { it.copy(hata = e.message) } }
                .collect { bellek ->
                    _uiDurum.update { it.copy(bellekBilgisi = bellek) }
                }
        }
    }

    // ─── Uyarı Tespiti ────────────────────────────────────────────────────────

    /**
     * Process listesini analiz eder, eşik değerlerini aşanları işaretler.
     *
     * Kurallar:
     * - PSS > 200MB → HIGH_RAM
     * - CPU zamanı > 1 saat → HIGH_CPU
     * - Offender listesinde → KNOWN_OFFENDER
     * - system_server CPU > %80 → SYSTEM_SERVER_SPIKE
     * - Visible tier ama overlay kullanmıyor olmalı → WRONG_OOM_TIER
     */
    private fun uyarilariTespit(liste: List<ProcessInfo>): List<AlertEvent> {
        val uyarilar = mutableListOf<AlertEvent>()

        for (proc in liste) {
            // RAM eşiği: 200MB = 204800 KB
            if (proc.pssKb > 204_800 && !proc.isSystemProcess) {
                uyarilar.add(AlertEvent(
                    packageName = proc.packageName,
                    alertType = AlertType.HIGH_RAM,
                    details = "${proc.pssKb / 1024}MB PSS kullanıyor"
                ))
            }

            // CPU zamanı > 1 saat parse et ("4:05.69" → toplam dakika)
            if (proc.cpuTimeFormatted.isNotEmpty()) {
                val dakika = cpuZamaniniDakikayaCevir(proc.cpuTimeFormatted)
                if (dakika > 60 && proc.oomTier in listOf(
                    OomTier.B_SERVICE, OomTier.CACHED, OomTier.VISIBLE
                )) {
                    uyarilar.add(AlertEvent(
                        packageName = proc.packageName,
                        alertType = AlertType.HIGH_CPU,
                        details = "${dakika.toInt()} dakika CPU zamanı (arka planda)"
                    ))
                }
            }

            // Bilinen offender
            if (proc.isKnownOffender) {
                uyarilar.add(AlertEvent(
                    packageName = proc.packageName,
                    alertType = AlertType.KNOWN_OFFENDER,
                    details = proc.offenderReason.take(100)
                ))
            }

            // system_server CPU spike
            if (proc.packageName == "system" && proc.cpuPercent > 80f) {
                uyarilar.add(AlertEvent(
                    packageName = "system",
                    alertType = AlertType.SYSTEM_SERVER_SPIKE,
                    details = "%${proc.cpuPercent.toInt()} CPU kullanımı tespit edildi"
                ))
            }
        }

        return uyarilar
    }

    /**
     * "4:05.69" veya "51:32.15" → toplam dakika
     */
    private fun cpuZamaniniDakikayaCevir(zaman: String): Float {
        return try {
            val parcalar = zaman.split(":")
            when (parcalar.size) {
                2 -> {
                    val dakika = parcalar[0].toFloat()
                    val saniye = parcalar[1].toFloat()
                    dakika + saniye / 60f
                }
                3 -> {
                    val saat = parcalar[0].toFloat()
                    val dakika = parcalar[1].toFloat()
                    saat * 60f + dakika
                }
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
    }

    // ─── Eylemler ─────────────────────────────────────────────────────────────

    fun processSeç(process: ProcessInfo) {
        _seciliProcess.value = process
    }

    fun processSeciminiTemizle() {
        _seciliProcess.value = null
    }

    fun zorlaKapat(paketAdi: String) {
        viewModelScope.launch {
            val sonuc = ShizukuActionEngine.zorlaKapat(paketAdi)
            logaEkle(paketAdi, sonuc.mesaj)
            _uiDurum.update { it.copy(sonEylemSonucu = sonuc.mesaj) }
        }
    }

    fun arkaPlaniKisitla(paketAdi: String) {
        viewModelScope.launch {
            val sonuc = ShizukuActionEngine.arkaPlaniKisitla(paketAdi)
            logaEkle(paketAdi, sonuc.mesaj)
            _uiDurum.update { it.copy(sonEylemSonucu = sonuc.mesaj) }
        }
    }

    fun overlayKaldir(paketAdi: String) {
        viewModelScope.launch {
            val sonuc = ShizukuActionEngine.overlayIzniniKaldir(paketAdi)
            logaEkle(paketAdi, sonuc.mesaj)
            _uiDurum.update { it.copy(sonEylemSonucu = sonuc.mesaj) }
        }
    }

    fun izinIptalEt(paketAdi: String, izin: String) {
        viewModelScope.launch {
            val sonuc = ShizukuActionEngine.izniIptalEt(paketAdi, izin)
            logaEkle(paketAdi, sonuc.mesaj)
            _uiDurum.update { it.copy(sonEylemSonucu = sonuc.mesaj) }
        }
    }

    /** Offender veritabanındaki tüm önerilen eylemleri tek seferde uygula */
    fun tamTemizle(paketAdi: String) {
        viewModelScope.launch {
            val sonuclar = ShizukuActionEngine.tamTemizle(paketAdi)
            val ozet = sonuclar.joinToString(" → ") { it.mesaj }
            logaEkle(paketAdi, "TAM TEMİZLEME: $ozet")
            _uiDurum.update { it.copy(sonEylemSonucu = ozet) }
        }
    }

    fun eylemSonucunuTemizle() {
        _uiDurum.update { it.copy(sonEylemSonucu = null) }
    }

    private fun logaEkle(paketAdi: String, eylem: String) {
        val giris = AlertEvent(
            packageName = paketAdi,
            alertType = AlertType.KNOWN_OFFENDER,
            details = "",
            actionTaken = eylem
        )
        _logGecmisi.update { it + giris }
    }
}
