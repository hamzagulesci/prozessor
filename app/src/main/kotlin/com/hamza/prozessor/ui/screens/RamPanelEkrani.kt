package com.hamza.prozessor.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamza.prozessor.MainViewModel
import com.hamza.prozessor.data.MemoryInfo
import com.hamza.prozessor.data.PressureLevel
import com.hamza.prozessor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RamPanelEkrani(viewModel: MainViewModel) {
    val uiDurum by viewModel.uiDurum.collectAsState()
    val bellek = uiDurum.bellekBilgisi

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = { Text("RAM Durumu", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (bellek == null) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    if (uiDurum.shizukuBagli) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            "Shizuku bağlı değil — veri yüklenemiyor",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // ─── Ana RAM Göstergesi ────────────────────────────────────────
                RamHalkasi(bellek)

                // ─── Detay Kartları ────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BellekKarti(
                        baslik = "Toplam",
                        deger = bellek.totalKb.bytlereMb(),
                        renk = RenkBilgi,
                        modifier = Modifier.weight(1f)
                    )
                    BellekKarti(
                        baslik = "Kullanılan",
                        deger = bellek.usedPssKb.bytlereMb(),
                        renk = when (bellek.pressureLevel) {
                            PressureLevel.CRITICAL -> RenkKritik
                            PressureLevel.WARNING  -> RenkUyari
                            PressureLevel.NORMAL   -> RenkNormal
                        },
                        modifier = Modifier.weight(1f)
                    )
                    BellekKarti(
                        baslik = "Boş",
                        deger = bellek.availableKb.bytlereMb(),
                        renk = RenkNormal,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ─── ZRAM / Swap Kartı ─────────────────────────────────────────
                ZramKarti(bellek)

                // ─── OOM Tier Breakdown ────────────────────────────────────────
                OomTierBreakdown(viewModel)

                // ─── Cached PSS ────────────────────────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Önbellek (Cached)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Android'in geri kazanabileceği bellek",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            bellek.cachedPssKb.bytlereMb(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RenkNormal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RamHalkasi(bellek: MemoryInfo) {
    val hedefYuzde = (bellek.usageRatio * 100f).coerceIn(0f, 100f)
    val animasyonluYuzde by animateFloatAsState(
        targetValue = hedefYuzde,
        animationSpec = tween(1000, easing = EaseOutCubic),
        label = "ram_animation"
    )

    val halkRengi = when (bellek.pressureLevel) {
        PressureLevel.CRITICAL -> RenkKritik
        PressureLevel.WARNING  -> RenkUyari
        PressureLevel.NORMAL   -> RenkNormal
    }

    val duktMetni = when (bellek.pressureLevel) {
        PressureLevel.CRITICAL -> "KRİTİK — Bellek Baskısı Yüksek"
        PressureLevel.WARNING  -> "UYARI — Bellek Dolmaya Başladı"
        PressureLevel.NORMAL   -> "NORMAL — Bellek Yeterli"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Halk çiz
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
                    val baslangicAcisi = -90f
                    val tarama = 360f * (animasyonluYuzde / 100f)

                    // Arka plan halkası
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = baslangicAcisi,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke
                    )
                    // Doluluk halkası
                    drawArc(
                        color = halkRengi,
                        startAngle = baslangicAcisi,
                        sweepAngle = tarama,
                        useCenter = false,
                        style = stroke
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%${animasyonluYuzde.toInt()}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = halkRengi
                    )
                    Text(
                        "kullanımda",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                color = halkRengi.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    duktMetni,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = halkRengi
                )
            }
        }
    }
}

@Composable
private fun ZramKarti(bellek: MemoryInfo) {
    val swapYuzde = (bellek.swapUsageRatio * 100f).coerceIn(0f, 100f)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "ZRAM / Swap",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "%${swapYuzde.toInt()} dolu",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (swapYuzde > 70f) RenkUyari else RenkNormal
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { bellek.swapUsageRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (swapYuzde > 70f) RenkUyari else RenkVurgu,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetrikSatiri("Fiziksel Kullanım", bellek.zramPhysicalKb.bytlereMb())
                MetrikSatiri("Swap Kullanımı", bellek.zramSwapUsedKb.bytlereMb())
                MetrikSatiri("Toplam Swap", bellek.zramTotalKb.bytlereMb())
            }
        }
    }
}

@Composable
private fun OomTierBreakdown(viewModel: MainViewModel) {
    val uiDurum by viewModel.uiDurum.collectAsState()

    // Her tier için toplam PSS hesapla
    val tierToplam = uiDurum.procesListesi
        .groupBy { it.oomTier }
        .mapValues { (_, liste) -> liste.sumOf { it.pssKb } }
        .entries
        .sortedByDescending { it.value }
        .take(6)

    if (tierToplam.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "OOM Tier Dağılımı",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            tierToplam.forEach { (tier, toplamKb) ->
                val tierRengi = when (tier) {
                    com.hamza.prozessor.data.OomTier.PERSISTENT,
                    com.hamza.prozessor.data.OomTier.PERSISTENT_SERVICE -> RenkTierPersistent
                    com.hamza.prozessor.data.OomTier.FOREGROUND -> RenkTierForeground
                    com.hamza.prozessor.data.OomTier.VISIBLE    -> RenkTierVisible
                    com.hamza.prozessor.data.OomTier.B_SERVICE  -> RenkTierBService
                    else -> RenkTierCached
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tierRengi)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tier.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        toplamKb.bytlereMb(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tierRengi
                    )
                }
            }
        }
    }
}

@Composable
private fun BellekKarti(
    baslik: String,
    deger: String,
    renk: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                baslik,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                deger,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = renk,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun MetrikSatiri(baslik: String, deger: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(baslik, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(deger, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold)
    }
}

// KB → MB/GB dönüştürücü
private fun Long.bytlereMb(): String {
    return when {
        this >= 1_048_576 -> String.format("%.1fGB", this / 1_048_576f)
        this >= 1024 -> "${this / 1024}MB"
        else -> "${this}KB"
    }
}
