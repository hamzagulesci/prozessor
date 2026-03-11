package com.hamza.prozessor.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamza.prozessor.MainViewModel
import com.hamza.prozessor.data.*
import com.hamza.prozessor.ui.theme.*

/**
 * Process listesi ekranı.
 * Her process için PSS, CPU%, OOM tier ve eylem butonu gösterir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessListeEkrani(viewModel: MainViewModel) {
    val uiDurum by viewModel.uiDurum.collectAsState()
    val seciliProcess by viewModel.seciliProcess.collectAsState()
    var aramaMetni by remember { mutableStateOf("") }
    var siralamaKriteri by remember { mutableStateOf(SiralamaKriteri.PSS_AZALAN) }

    // Filtreleme + sıralama
    val filtrelenmisListe = remember(uiDurum.procesListesi, aramaMetni, siralamaKriteri) {
        uiDurum.procesListesi
            .filter { proc ->
                aramaMetni.isEmpty() ||
                proc.packageName.contains(aramaMetni, ignoreCase = true) ||
                proc.displayName.contains(aramaMetni, ignoreCase = true)
            }
            .let { liste ->
                when (siralamaKriteri) {
                    SiralamaKriteri.PSS_AZALAN -> liste.sortedByDescending { it.pssKb }
                    SiralamaKriteri.CPU_AZALAN -> liste.sortedByDescending { it.cpuPercent }
                    SiralamaKriteri.TIER       -> liste.sortedBy { it.oomTier.priority }
                    SiralamaKriteri.OFFENDER   -> liste.sortedByDescending { if (it.isKnownOffender) 1 else 0 }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ─── Üst bar ──────────────────────────────────────────────────────────
        TopAppBar(
            title = {
                Text("Süreç Monitörü", fontWeight = FontWeight.Bold)
            },
            actions = {
                // Sıralama menüsü
                var menuAcik by remember { mutableStateOf(false) }
                IconButton(onClick = { menuAcik = true }) {
                    Icon(Icons.Default.Sort, "Sırala")
                }
                DropdownMenu(expanded = menuAcik, onDismissRequest = { menuAcik = false }) {
                    SiralamaKriteri.entries.forEach { kriter ->
                        DropdownMenuItem(
                            text = { Text(kriter.goruntulemeAdi) },
                            onClick = {
                                siralamaKriteri = kriter
                                menuAcik = false
                            },
                            leadingIcon = if (siralamaKriteri == kriter) {
                                { Icon(Icons.Default.Check, null) }
                            } else null
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // ─── Arama kutusu ─────────────────────────────────────────────────────
        OutlinedTextField(
            value = aramaMetni,
            onValueChange = { aramaMetni = it },
            placeholder = { Text("Paket adı ara...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (aramaMetni.isNotEmpty()) {
                    IconButton(onClick = { aramaMetni = "" }) {
                        Icon(Icons.Default.Clear, null)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true
        )

        // Özet satırı
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${filtrelenmisListe.size} süreç",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiDurum.aktifUyarilar.isNotEmpty()) {
                Surface(
                    color = RenkKritik.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "${uiDurum.aktifUyarilar.size} uyarı",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = RenkKritik
                    )
                }
            }
        }

        // ─── Process Listesi ──────────────────────────────────────────────────
        if (uiDurum.yukleniyor) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = filtrelenmisListe,
                    key = { it.pid }
                ) { proc ->
                    ProcessKarti(
                        proc = proc,
                        onClick = { viewModel.processSeç(proc) }
                    )
                }
            }
        }
    }

    // ─── Eylem Bottom Sheet ───────────────────────────────────────────────────
    seciliProcess?.let { proc ->
        EylemBottomSheet(
            proc = proc,
            shizukuBagli = uiDurum.shizukuBagli,
            onKapat = { viewModel.processSeciminiTemizle() },
            onZorlaKapat = { viewModel.zorlaKapat(proc.packageName) },
            onArkaPlaniKisitla = { viewModel.arkaPlaniKisitla(proc.packageName) },
            onOverlayKaldir = { viewModel.overlayKaldir(proc.packageName) },
            onTamTemizle = { viewModel.tamTemizle(proc.packageName) },
            onIzinIptalEt = { izin -> viewModel.izinIptalEt(proc.packageName, izin) }
        )
    }
}

// ─── Process Kartı ────────────────────────────────────────────────────────────

@Composable
fun ProcessKarti(proc: ProcessInfo, onClick: () -> Unit) {
    val tierRengi = remember(proc.oomTier) {
        when (proc.oomTier) {
            OomTier.PERSISTENT, OomTier.PERSISTENT_SERVICE -> RenkTierPersistent
            OomTier.FOREGROUND -> RenkTierForeground
            OomTier.VISIBLE -> RenkTierVisible
            OomTier.B_SERVICE -> RenkTierBService
            OomTier.CACHED -> RenkTierCached
            else -> Color.Gray
        }
    }

    // RAM baskı rengi
    val ramRengi = when {
        proc.pssKb > 204_800 -> RenkKritik    // > 200MB
        proc.pssKb > 102_400 -> RenkUyari     // > 100MB
        else -> RenkNormal
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (proc.isKnownOffender)
                RenkKritik.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tier renk çubuğu
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tierRengi)
            )

            Spacer(Modifier.width(12.dp))

            // Uygulama bilgileri
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = proc.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (proc.isKnownOffender) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Bilinen tehdit",
                            tint = RenkKritik,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Text(
                    text = proc.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tier etiketi
                    Surface(
                        color = tierRengi.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            proc.oomTier.displayName,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = tierRengi,
                            fontSize = 10.sp
                        )
                    }

                    // PID
                    Text(
                        "PID: ${proc.pid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }

            // RAM + CPU değerleri
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${proc.pssKb / 1024}MB",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = ramRengi
                )
                if (proc.cpuPercent > 0f) {
                    Text(
                        text = "%${proc.cpuPercent.let {
                            if (it < 10f) String.format("%.1f", it) else it.toInt().toString()
                        }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (proc.cpuPercent > 20f) RenkKritik else RenkMetinIkincil
                    )
                }
                if (proc.cpuTimeFormatted.isNotEmpty()) {
                    Text(
                        proc.cpuTimeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

// ─── Eylem Bottom Sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EylemBottomSheet(
    proc: ProcessInfo,
    shizukuBagli: Boolean,
    onKapat: () -> Unit,
    onZorlaKapat: () -> Unit,
    onArkaPlaniKisitla: () -> Unit,
    onOverlayKaldir: () -> Unit,
    onTamTemizle: () -> Unit,
    onIzinIptalEt: (String) -> Unit
) {
    var binderGorunur by remember { mutableStateOf(false) }
    val offenderGirisi = com.hamza.prozessor.data.OffenderDatabase.findOffender(proc.packageName)

    ModalBottomSheet(
        onDismissRequest = onKapat,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Başlık
            Text(
                proc.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                proc.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(4.dp))

            // Kısa bilgi satırı
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("${proc.pssKb / 1024}MB PSS")
                if (proc.cpuPercent > 0f) Chip("%${proc.cpuPercent} CPU")
                Chip(proc.oomTier.displayName)
            }

            // Offender açıklaması
            if (proc.isKnownOffender && offenderGirisi != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = RenkKritik.copy(0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        offenderGirisi.reason.take(200),
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = RenkKritik
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Eylem butonları
            val butonModifier = Modifier.fillMaxWidth()

            if (!shizukuBagli) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "⚠ Shizuku bağlı değil — eylemler devre dışı",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Zorla kapat
            OutlinedButton(
                onClick = { onZorlaKapat(); onKapat() },
                modifier = butonModifier,
                enabled = shizukuBagli
            ) {
                Icon(Icons.Default.Stop, null)
                Spacer(Modifier.width(8.dp))
                Text("Zorla Kapat")
            }

            Spacer(Modifier.height(6.dp))

            // Arka plan kısıtla
            OutlinedButton(
                onClick = { onArkaPlaniKisitla(); onKapat() },
                modifier = butonModifier,
                enabled = shizukuBagli
            ) {
                Icon(Icons.Default.Block, null)
                Spacer(Modifier.width(8.dp))
                Text("Arka Planı Kısıtla (RUN_IN_BACKGROUND deny)")
            }

            Spacer(Modifier.height(6.dp))

            // Overlay kaldır (sadece Visible tier için)
            if (proc.oomTier == OomTier.VISIBLE) {
                OutlinedButton(
                    onClick = { onOverlayKaldir(); onKapat() },
                    modifier = butonModifier,
                    enabled = shizukuBagli
                ) {
                    Icon(Icons.Default.Layers, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Overlay İznini Kaldır (SYSTEM_ALERT_WINDOW)")
                }
                Spacer(Modifier.height(6.dp))
            }

            // İzin iptalleri (offender listesindeyse)
            offenderGirisi?.permissionsToRevoke?.forEach { izin ->
                val izinKisa = izin.substringAfterLast(".")
                OutlinedButton(
                    onClick = { onIzinIptalEt(izin) },
                    modifier = butonModifier,
                    enabled = shizukuBagli
                ) {
                    Icon(Icons.Default.RemoveCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("İptal: $izinKisa")
                }
                Spacer(Modifier.height(4.dp))
            }

            // Tam Temizle (kırmızı — tüm eylemleri sıralı çalıştırır)
            if (proc.isKnownOffender) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onTamTemizle(); onKapat() },
                    modifier = butonModifier,
                    enabled = shizukuBagli,
                    colors = ButtonDefaults.buttonColors(containerColor = RenkKritik)
                ) {
                    Icon(Icons.Default.CleaningServices, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tam Temizle (Tüm Kısıtlamalar + Durdur)")
                }
            }

            // Binder çağrısı görüntüle (öğrenme amaçlı)
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = { binderGorunur = !binderGorunur },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Code, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (binderGorunur) "Binder Çağrısını Gizle"
                    else "Binder Çağrısını Göster",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (binderGorunur) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = """
// Shizuku UserService üzerinden yapılan AIDL çağrıları:
userService.forceStopPackage("${proc.packageName}")
  → am force-stop ${proc.packageName}
  → IActivityManager.forceStopPackage("${proc.packageName}", 0)

userService.restrictBackground("${proc.packageName}")
  → cmd appops set ${proc.packageName} RUN_IN_BACKGROUND deny
  → AppOpsManager.setMode(OP_RUN_IN_BACKGROUND, uid, MODE_IGNORED)

userService.revokeOverlayPermission("${proc.packageName}")
  → cmd appops set ${proc.packageName} SYSTEM_ALERT_WINDOW deny
                        """.trimIndent(),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = RenkVurgucAcik,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(metin: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            metin,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

enum class SiralamaKriteri(val goruntulemeAdi: String) {
    PSS_AZALAN("RAM (Yüksekten Düşüğe)"),
    CPU_AZALAN("CPU (Yüksekten Düşüğe)"),
    TIER("OOM Tier"),
    OFFENDER("Tehditler Önce")
}
