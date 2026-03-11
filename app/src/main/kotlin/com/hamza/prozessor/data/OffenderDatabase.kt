package com.hamza.prozessor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore singleton extension
val Context.offenderDataStore: DataStore<Preferences> by preferencesDataStore(name = "offenders")

/**
 * Bilinen agresif arka plan uygulamalarının veritabanı.
 *
 * Kaynak: dump analizi + Türk kullanıcı raporları
 * Güncelleme: manuel araştırma, Play Store incelemeleri
 */
object OffenderDatabase {

    /**
     * Tek bir offender kaydı.
     * [autoAction]: tespit edildiğinde otomatik ne yapılacak
     */
    data class OffenderEntry(
        val packageName: String,
        val displayName: String,
        val category: OffenderCategory,
        val reason: String,           // Neden agresif olduğunun açıklaması
        val knownBehaviors: List<String>,
        val autoAction: AutoAction = AutoAction.NOTIFY,
        val permissionsToRevoke: List<String> = emptyList()
    )

    enum class OffenderCategory {
        TURKISH_CARRIER,    // Türk operatör uygulamaları
        TURKISH_BANK,       // Türk bankacılık uygulamaları
        TURKISH_GOV,        // Türkiye devlet uygulamaları
        SOCIAL_MEDIA,       // Sosyal medya (yüksek arka plan tüketimi)
        SYSTEM_BLOAT,       // Sistem şişkinliği
        ADWARE              // Reklam/izleme ağırlıklı
    }

    enum class AutoAction {
        NOTIFY,             // Sadece bildirim gönder
        RESTRICT_BACKGROUND,// RUN_IN_BACKGROUND deny
        FORCE_STOP          // Zorla durdur
    }

    /**
     * Araştırılmış offender listesi.
     * Veriler: gerçek dump analizi + Play Store izinleri + kullanıcı raporları
     */
    val HARDCODED_LIST: List<OffenderEntry> = listOf(

        // ─── Türk Operatör Uygulamaları ──────────────────────────────────────
        OffenderEntry(
            packageName = "com.vodafone.selfservis",
            displayName = "Vodafone TR Selfservis",
            category = OffenderCategory.TURKISH_CARRIER,
            reason = "Dump'ta 9h+ CPU zamanı, Visible tier'da oturuyor (overlay kullanıyor), " +
                    "meşru arka plan işlevi yok. Muhtemelen push notification için sürekli polling.",
            knownBehaviors = listOf(
                "SYSTEM_ALERT_WINDOW izni ile ekran üstü overlay tutuyor",
                "RUN_IN_BACKGROUND ile sürekli açık kalıyor",
                "Konum izni istiyor (operatör uygulaması için gereksiz)",
                "Kamera/mikrofon arka plan erişimi"
            ),
            autoAction = AutoAction.RESTRICT_BACKGROUND,
            permissionsToRevoke = listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO"
            )
        ),
        OffenderEntry(
            packageName = "com.turkcell.pes",
            displayName = "Turkcell Pasaj",
            category = OffenderCategory.TURKISH_CARRIER,
            reason = "Turkcell'in e-ticaret uygulaması, operatör uygulaması bağlamında " +
                    "sürekli arka planda çalışıyor. Play Store yorumlarında pil şikayeti yaygın.",
            knownBehaviors = listOf(
                "Arka planda periyodik network çağrıları",
                "GMS üzerinden push + kendi polling mekanizması (çift katman)",
                "Boot completed receiver ile otomatik başlıyor"
            ),
            autoAction = AutoAction.RESTRICT_BACKGROUND,
            permissionsToRevoke = listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.READ_CONTACTS"
            )
        ),
        OffenderEntry(
            packageName = "com.turktelekom.mytt",
            displayName = "Türk Telekom MyTT",
            category = OffenderCategory.TURKISH_CARRIER,
            reason = "Türk Telekom müşteri uygulaması. Operatör seviyesinde yetkilendirme " +
                    "nedeniyle kaldırılması zor; arka plan kısıtlaması yeterli.",
            knownBehaviors = listOf(
                "Periyodik fatura/kota kontrolü için wake lock",
                "SMS izni istiyor (OTP bahanesiyle kalıcı tutuyor)"
            ),
            autoAction = AutoAction.RESTRICT_BACKGROUND,
            permissionsToRevoke = listOf(
                "android.permission.ACCESS_FINE_LOCATION"
            )
        ),

        // ─── Türk Bankacılık Uygulamaları ────────────────────────────────────
        OffenderEntry(
            packageName = "com.ykb.android",
            displayName = "Yapı Kredi Mobil",
            category = OffenderCategory.TURKISH_BANK,
            reason = "Play Integrity retry loop şüphesi: yoğun CPU kullanımı çok kısa " +
                    "sürede (3 dakikada 331MB), cihazın root/debug durumunu sürekli kontrol ediyor.",
            knownBehaviors = listOf(
                "Play Integrity API ile anti-tamper kontrolü (arka planda tekrarlı)",
                "Kısa sürede yüksek CPU → muhtemelen SafetyNet/Integrity döngüsü",
                "Foreground service tutmuyor ama yüksek öncelikli arka plan istiyor"
            ),
            autoAction = AutoAction.NOTIFY, // Bankacılık uygulaması, dikkatli ol
            permissionsToRevoke = emptyList()
        ),
        OffenderEntry(
            packageName = "com.garantibbva.mobile.android",
            displayName = "Garanti BBVA Mobil",
            category = OffenderCategory.TURKISH_BANK,
            reason = "GMS foreground service + kendi polling mekanizması. " +
                    "Para bildirimleri için sürekli açık kalma eğilimi.",
            knownBehaviors = listOf(
                "Foreground service (bildirim çubuğunda görünmese de)",
                "Konum izni aktif kullanım dışında da çalışıyor"
            ),
            autoAction = AutoAction.NOTIFY
        ),
        OffenderEntry(
            packageName = "com.akbank.android.apps.akbank",
            displayName = "Akbank",
            category = OffenderCategory.TURKISH_BANK,
            reason = "Diğer Türk banka uygulamalarıyla benzer pattern. " +
                    "Push notification için aşırı kaynak kullanımı bildirilmiş.",
            knownBehaviors = listOf(
                "Firebase + kendi push katmanı",
                "Boot receiver ile otomatik başlıyor"
            ),
            autoAction = AutoAction.NOTIFY
        ),

        // ─── Türk Devlet Uygulamaları ─────────────────────────────────────────
        OffenderEntry(
            packageName = "tr.gov.turkiye.edevlet.kapisi",
            displayName = "e-Devlet Kapısı",
            category = OffenderCategory.TURKISH_GOV,
            reason = "Cached tier'da oturuyor ama önceki snapshot'larda yüksek " +
                    "konum erişimi. Aktif kullanım gerektiren uygulama, arka planda işi yok.",
            knownBehaviors = listOf(
                "Konum izni arka planda kullanıyor (neden?)",
                "Kamera izni kalıcı tutuyor"
            ),
            autoAction = AutoAction.RESTRICT_BACKGROUND,
            permissionsToRevoke = listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION"
            )
        ),

        // ─── Sosyal Medya (Yüksek Tüketim) ───────────────────────────────────
        OffenderEntry(
            packageName = "com.zhiliaoapp.musically",
            displayName = "TikTok",
            category = OffenderCategory.SOCIAL_MEDIA,
            reason = "GERÇEK DUMP VERİSİ: B Service tier'da 697MB PSS kullanıyor. " +
                    "Hiçbir meşru B Services işlevi yok. Tek kelimeyle: RAM çöplüğü.",
            knownBehaviors = listOf(
                "B Services tier'da ~700MB tutuyor (launcher3'ten fazla)",
                "Arka planda video pre-fetch yapıyor",
                "Agresif wake lock ile ekran kapalıyken bile çalışıyor",
                "Çoklu process açıyor (ana + background process)"
            ),
            autoAction = AutoAction.RESTRICT_BACKGROUND,
            permissionsToRevoke = listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.READ_CONTACTS",
                "android.permission.READ_CALL_LOG"
            )
        ),
        OffenderEntry(
            packageName = "com.instagram.android",
            displayName = "Instagram",
            category = OffenderCategory.SOCIAL_MEDIA,
            reason = "GERÇEK DUMP: Foreground (190MB) + fbns B Service (71MB) = 261MB toplam. " +
                    "fbns = Facebook Notification Service, ayrı process olarak koşuyor.",
            knownBehaviors = listOf(
                "fbns process ayrı çalışarak kendi RAM'ini tutuyor",
                "Arka planda sürekli sync",
                "Konum + kamera arka plan erişimi"
            ),
            autoAction = AutoAction.RESTRICT_BACKGROUND,
            permissionsToRevoke = listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION"
            )
        ),
        OffenderEntry(
            packageName = "com.facebook.katana",
            displayName = "Facebook",
            category = OffenderCategory.SOCIAL_MEDIA,
            reason = "Cached tier'da 133MB PSS. Facebook'un arka plan sync agresifliği " +
                    "Android topluluğunda iyi belgelenmiş.",
            knownBehaviors = listOf(
                "Birden fazla background service",
                "Contacts sync ile adres defteri çekiyor",
                "Agresif re-launch mekanizması"
            ),
            autoAction = AutoAction.RESTRICT_BACKGROUND,
            permissionsToRevoke = listOf(
                "android.permission.READ_CONTACTS",
                "android.permission.ACCESS_FINE_LOCATION"
            )
        ),

        // ─── Sistem Şişkinliği ────────────────────────────────────────────────
        OffenderEntry(
            packageName = "com.paget96.batteryguru",
            displayName = "BatteryGuru",
            category = OffenderCategory.SYSTEM_BLOAT,
            reason = "GERÇEK DUMP: %17.8 CPU (en yüksek user-space uygulama), " +
                    "20 dakika 7 saniye CPU zamanı. Pil yönetim uygulaması pilin kendisini yiyor.",
            knownBehaviors = listOf(
                "battery_service process olarak sürekli foreground'da",
                "%17.8 anlık CPU kullanımı (sistem servislerinden fazla)",
                "Pil optimizasyonu yapacağım derken CPU tüketiyor — ironik"
            ),
            autoAction = AutoAction.RESTRICT_BACKGROUND
        ),
        OffenderEntry(
            packageName = "com.google.android.gms",
            displayName = "Google Play Services",
            category = OffenderCategory.SYSTEM_BLOAT,
            reason = "Kaçınılmaz ama kontrol edilebilir. gms.persistent + gms ikili process " +
                    "ile toplamda ~300MB tutuyor. Konum iznini en çok tüketen servis.",
            knownBehaviors = listOf(
                "gms.persistent + gms + gms.ui = üç ayrı process",
                "Background location sürekli aktif",
                "Periyodik attestation ve device check"
            ),
            autoAction = AutoAction.NOTIFY // GMS'e dokunmak riskli, sadece bildir
        )
    )

    /** Paket adına göre offender kaydını bul */
    fun findOffender(packageName: String): OffenderEntry? =
        HARDCODED_LIST.find { it.packageName == packageName }

    /** Offender listesinde var mı? */
    fun isOffender(packageName: String): Boolean =
        HARDCODED_LIST.any { it.packageName == packageName }
}

/**
 * Kullanıcının özel eklediği offender paket adlarını DataStore'da saklar.
 */
class UserOffenderStore(private val context: Context) {

    private val KEY_CUSTOM_PACKAGES = stringSetPreferencesKey("custom_offender_packages")

    val customPackages: Flow<Set<String>> = context.offenderDataStore.data
        .map { prefs -> prefs[KEY_CUSTOM_PACKAGES] ?: emptySet() }

    suspend fun addPackage(packageName: String) {
        context.offenderDataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_PACKAGES] ?: emptySet()
            prefs[KEY_CUSTOM_PACKAGES] = current + packageName
        }
    }

    suspend fun removePackage(packageName: String) {
        context.offenderDataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_PACKAGES] ?: emptySet()
            prefs[KEY_CUSTOM_PACKAGES] = current - packageName
        }
    }
}
