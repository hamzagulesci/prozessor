package com.hamza.prozessor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hamza.prozessor.MainViewModel
import com.hamza.prozessor.data.OffenderDatabase
import com.hamza.prozessor.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════════
// TEHDİT LİSTESİ EKRANI
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TehditListesiEkrani(viewModel: MainViewModel) {
    val uiDurum by viewModel.uiDurum.collectAsState()

    // Şu an çalışan offender'ları bul
    val calısanOffenderlar = remember(uiDurum.procesListesi) {
        uiDurum.procesListesi.filter { it.isKnownOffender }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Bilinen Tehditler", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Aktif tehditler başlığı
            if (calısanOffenderlar.isNotEmpty()) {
                item {
                    SectionBaslik(
                        "🔴 Şu An Çalışanlar (${calısanOffenderlar.size})",
                        renk = RenkKritik
                    )
                }
                items(calısanOffenderlar) { proc ->
                    val offenderGirisi = OffenderDatabase.findOffender(proc.packageName)
                    AktifTehditKarti(
                        isim = proc.displayName,
                        paketAdi = proc.packageName,
                        pssKb = proc.pssKb,
                        tier = proc.oomTier.displayName,
                        neden = offenderGirisi?.reason ?: "",
                        onZorlaKapat = { viewModel.zorlaKapat(proc.packageName) },
                        onKisitla = { viewModel.arkaPlaniKisitla(proc.packageName) },
                        onTamTemizle = { viewModel.tamTemizle(proc.packageName) },
                        shizukuBagli = uiDurum.shizukuBagli
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // Tüm bilinen tehditler listesi
            item {
                SectionBaslik("📋 Bilinen Tehditler Veritabanı (${OffenderDatabase.HARDCODED_LIST.size})")
            }

            items(OffenderDatabase.HARDCODED_LIST) { offender ->
                val calisiyor = calısanOffenderlar.any { it.packageName == offender.packageName }
                OffenderVeritabaniKarti(offender, calisiyor)
            }
        }
    }
}

@Composable
private fun SectionBaslik(metin: String, renk: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        metin,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = renk,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun AktifTehditKarti(
    isim: String,
    paketAdi: String,
    pssKb: Long,
    tier: String,
    neden: String,
    onZorlaKapat: () -> Unit,
    onKisitla: () -> Unit,
    onTamTemizle: () -> Unit,
    shizukuBagli: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = RenkKritik.copy(alpha = 0.08f)
        ),
        border = CardDefaults.outlinedCardBorder().let {
            androidx.compose.foundation.BorderStroke(1.dp, RenkKritik.copy(0.3f))
        }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(isim, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(paketAdi, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${pssKb / 1024}MB · $tier",
                        style = MaterialTheme.typography.labelSmall, color = RenkUyari)
                }
                Surface(
                    color = RenkKritik.copy(0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("AKTİF",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = RenkKritik,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (neden.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    neden.take(120) + if (neden.length > 120) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onZorlaKapat,
                    enabled = shizukuBagli,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Durdur", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onKisitla,
                    enabled = shizukuBagli,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kısıtla", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onTamTemizle,
                    enabled = shizukuBagli,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RenkKritik),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("Temizle", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun OffenderVeritabaniKarti(
    offender: OffenderDatabase.OffenderEntry,
    aktifCalisıyor: Boolean
) {
    var genisletilmis by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(offender.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (aktifCalisıyor) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = RenkKritik.copy(0.2f),
                                shape = RoundedCornerShape(3.dp)) {
                                Text("●", modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    color = RenkKritik,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Surface(
                        color = when (offender.category) {
                            OffenderDatabase.OffenderCategory.TURKISH_CARRIER -> RenkUyari.copy(0.15f)
                            OffenderDatabase.OffenderCategory.TURKISH_BANK    -> RenkBilgi.copy(0.15f)
                            OffenderDatabase.OffenderCategory.TURKISH_GOV     -> RenkVurgu.copy(0.15f)
                            OffenderDatabase.OffenderCategory.SOCIAL_MEDIA    -> RenkTierBService.copy(0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            offender.category.name.replace("_", " "),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(onClick = { genisletilmis = !genisletilmis }) {
                    Icon(
                        if (genisletilmis) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null
                    )
                }
            }

            if (genisletilmis) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "📦 ${offender.packageName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    offender.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (offender.knownBehaviors.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Bilinen davranışlar:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold)
                    offender.knownBehaviors.forEach { davranis ->
                        Text("• $davranis",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (offender.permissionsToRevoke.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("İptal edilmesi önerilen izinler:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = RenkUyari)
                    offender.permissionsToRevoke.forEach { izin ->
                        Text("• ${izin.substringAfterLast(".")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RenkUyari)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AYARLAR EKRANI (Shizuku Kurulum + Log Export + Wireless ADB)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyarlarEkrani(viewModel: MainViewModel) {
    val uiDurum by viewModel.uiDurum.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Ayarlar & Kurulum", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        androidx.compose.foundation.rememberScrollState().let { scroll ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ─── Shizuku Durumu ────────────────────────────────────────────
                ShizukuDurumKarti(uiDurum.shizukuBagli)

                // ─── Wireless ADB Kurulum ──────────────────────────────────────
                KurulumAdimlariKarti()

                // ─── Sürüm Bilgisi ─────────────────────────────────────────────
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Uygulama Hakkında", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        BilgiSatiri("Uygulama", "Prozessor v1.0")
                        BilgiSatiri("Hedef Cihaz", "curtana (Redmi Note 9 Pro)")
                        BilgiSatiri("ROM", "crDroid 12.8 / Android 16")
                        BilgiSatiri("Root", "Gerekmiyor — Shizuku yeterli")
                        BilgiSatiri("İnternet", "Yok — Tamamen offline")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShizukuDurumKarti(bagli: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (bagli) RenkNormal.copy(0.1f) else RenkKritik.copy(0.1f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (bagli) RenkNormal.copy(0.3f) else RenkKritik.copy(0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (bagli) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (bagli) RenkNormal else RenkKritik,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (bagli) "Shizuku Bağlı ✓" else "Shizuku Bağlı Değil",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (bagli) RenkNormal else RenkKritik
                )
                Text(
                    if (bagli) "Tüm eylemler kullanılabilir"
                    else "Aşağıdaki adımları takip ederek Shizuku'yu başlat",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KurulumAdimlariKarti() {
    var genisletilmis by remember { mutableStateOf(true) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Shizuku Kurulum Adımları",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                IconButton(onClick = { genisletilmis = !genisletilmis }) {
                    Icon(if (genisletilmis) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }

            if (genisletilmis) {
                Spacer(Modifier.height(12.dp))

                KurulumAdimi(
                    numara = "1",
                    baslik = "USB ile PC'ye bağlan",
                    aciklama = "USB hata ayıklama modunun aktif olduğundan emin ol",
                    komut = null
                )
                KurulumAdimi(
                    numara = "2",
                    baslik = "Shizuku'yu ADB ile başlat (bir kez yeterli)",
                    aciklama = "Bu komut Shizuku'ya shell yetkisi verir",
                    komut = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/files/start.sh"
                )
                KurulumAdimi(
                    numara = "3",
                    baslik = "Wireless ADB'yi etkinleştir",
                    aciklama = "USB bağlantısını kesmeden önce TCP modunu aç",
                    komut = "adb tcpip 5555"
                )
                KurulumAdimi(
                    numara = "4",
                    baslik = "Kablosuz bağlan",
                    aciklama = "Cihazın IP adresini Ayarlar > Cihaz Bilgileri'nden öğren",
                    komut = "adb connect 192.168.x.x:5555"
                )
                KurulumAdimi(
                    numara = "5",
                    baslik = "Uygulamayı kur (sideload)",
                    aciklama = "APK'yı USB veya kablosuz ADB ile yükle",
                    komut = "adb install prozessor.apk"
                )

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = RenkBilgi.copy(0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "💡 crDroid 12.8'de Kablosuz ADB: Ayarlar > Geliştirici Seçenekleri > " +
                        "Kablosuz Hata Ayıklama bölümünden QR kod ile eşleştirme de çalışır. " +
                        "Shizuku'yu her reboot'ta yeniden başlatman gerekebilir.",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = RenkBilgi
                    )
                }
            }
        }
    }
}

@Composable
private fun KurulumAdimi(
    numara: String,
    baslik: String,
    aciklama: String,
    komut: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = RenkVurgu,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(numara, style = MaterialTheme.typography.labelMedium,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(baslik, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
            Text(aciklama, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (komut != null) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        komut,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = RenkVurgucAcik
                    )
                }
            }
        }
    }
}

@Composable
private fun BilgiSatiri(baslik: String, deger: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(baslik, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(deger, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold)
    }
}
