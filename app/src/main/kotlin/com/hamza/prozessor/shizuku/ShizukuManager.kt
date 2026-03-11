package com.hamza.prozessor.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.hamza.prozessor.IUserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

private const val TAG = "ShizukuManager"

/**
 * Shizuku bağlantı durumunu ve UserService yaşam döngüsünü yönetir.
 *
 * Kullanım:
 * 1. ShizukuManager.baslat() → Shizuku dinleyicilerini kaydet
 * 2. ShizukuManager.userService değerini AIDL çağrıları için kullan
 * 3. ShizukuManager.durdur() → Activity/Service destroy'da çağır
 */
object ShizukuManager {

    sealed class ShizukuDurumu {
        object Bagli : ShizukuDurumu()
        object Baglanti_Kesildi : ShizukuDurumu()
        data class Hata(val mesaj: String) : ShizukuDurumu()
        object Izin_Bekleniyor : ShizukuDurumu()
        object Baslatilmamis : ShizukuDurumu()
    }

    private val _durum = MutableStateFlow<ShizukuDurumu>(ShizukuDurumu.Baslatilmamis)
    val durum: StateFlow<ShizukuDurumu> = _durum

    private val _userService = MutableStateFlow<IUserService?>(null)
    val userService: StateFlow<IUserService?> = _userService

    // UserService bağlantı konfigürasyonu
    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.hamza.prozessor", "com.hamza.prozessor.shizuku.UserServiceWrapper")
    )
        .daemon(false)        // Daemon değil — Shizuku yeniden başladığında ölsün
        .processNameSuffix("user_service") // Process adı: com.hamza.prozessor:user_service
        .debuggable(false)
        .version(1)

    // UserService binder bağlantı geri çağırımları
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                _userService.value = IUserService.Stub.asInterface(binder)
                _durum.value = ShizukuDurumu.Bagli
                Log.d(TAG, "UserService bağlandı, versiyon: ${_userService.value?.version}")
            } else {
                _durum.value = ShizukuDurumu.Hata("Binder geçersiz veya ölü")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _userService.value = null
            _durum.value = ShizukuDurumu.Baglanti_Kesildi
            Log.w(TAG, "UserService bağlantısı kesildi")
        }
    }

    // Shizuku ölüm bildirimi
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _userService.value = null
        _durum.value = ShizukuDurumu.Baglanti_Kesildi
        Log.w(TAG, "Shizuku binder öldü")
    }

    // Shizuku hazır bildirimi
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder alındı")
        izinkontrolVeBaglantıKur()
    }

    // İzin sonucu geri çağırımı
    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001) {
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    userServiceBaglantisiKur()
                } else {
                    _durum.value = ShizukuDurumu.Hata("Shizuku izni reddedildi")
                }
            }
        }

    /**
     * Shizuku dinleyicilerini kaydet. Activity.onCreate()'de çağır.
     */
    fun baslat() {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        // Shizuku zaten çalışıyorsa hemen bağlan
        if (Shizuku.pingBinder()) {
            izinkontrolVeBaglantıKur()
        } else {
            _durum.value = ShizukuDurumu.Baglanti_Kesildi
        }
    }

    /**
     * Dinleyicileri kaldır. Activity.onDestroy()'da çağır.
     */
    fun durdur() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)

        // UserService'i temizle ama yok etme — arka planda çalışmaya devam etsin
        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, false)
        } catch (e: Exception) {
            Log.e(TAG, "UserService unbind hatası: ${e.message}")
        }
    }

    /**
     * İzin durumunu kontrol et, gerekirse iste.
     */
    private fun izinkontrolVeBaglantıKur() {
        try {
            when {
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    userServiceBaglantisiKur()
                }
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    _durum.value = ShizukuDurumu.Izin_Bekleniyor
                }
                else -> {
                    _durum.value = ShizukuDurumu.Izin_Bekleniyor
                    Shizuku.requestPermission(1001)
                }
            }
        } catch (e: Exception) {
            _durum.value = ShizukuDurumu.Hata("İzin kontrolü başarısız: ${e.message}")
        }
    }

    /**
     * UserService'i başlat ve bağlan.
     */
    private fun userServiceBaglantisiKur() {
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        } catch (e: Exception) {
            _durum.value = ShizukuDurumu.Hata("UserService başlatılamadı: ${e.message}")
            Log.e(TAG, "bindUserService hatası", e)
        }
    }

    /**
     * UserService'e güvenli erişim — null kontrolü ile.
     * [blok]: IUserService null değilse çalıştırılacak lambda
     */
    fun <T> withService(blok: (IUserService) -> T): T? {
        return _userService.value?.let { servis ->
            try {
                blok(servis)
            } catch (e: android.os.DeadObjectException) {
                _userService.value = null
                _durum.value = ShizukuDurumu.Baglanti_Kesildi
                Log.e(TAG, "UserService öldü (DeadObjectException)")
                null
            } catch (e: Exception) {
                Log.e(TAG, "UserService çağrısı başarısız: ${e.message}")
                null
            }
        }
    }
}
