package com.hamza.prozessor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Renk Paleti ─────────────────────────────────────────────────────────────
val RenkArkaPlan       = Color(0xFF0D0D14)  // Çok koyu lacivert
val RenkYuzey          = Color(0xFF1A1A2E)  // Yüzey kartları
val RenkYuzeyYuksek    = Color(0xFF22223A)  // Yüksek kart
val RenkVurgu          = Color(0xFF7C3AED)  // Mor vurgu
val RenkVurgucAcik     = Color(0xFF9B7EC8)  // Açık mor
val RenkMetin          = Color(0xFFE8E8F0)  // Ana metin
val RenkMetinIkincil   = Color(0xFF8888AA)  // İkincil metin

// Durum renkleri
val RenkNormal         = Color(0xFF22C55E)  // Yeşil — %70 altı
val RenkUyari          = Color(0xFFF59E0B)  // Sarı — %70–85
val RenkKritik         = Color(0xFFEF4444)  // Kırmızı — %85 üstü
val RenkBilgi          = Color(0xFF3B82F6)  // Mavi — bilgi

// OOM tier renkleri
val RenkTierPersistent = Color(0xFFDC2626)
val RenkTierForeground = Color(0xFFF97316)
val RenkTierVisible    = Color(0xFFEAB308)
val RenkTierBService   = Color(0xFF8B5CF6)
val RenkTierCached     = Color(0xFF6B7280)

private val ProzessorKoyuTema = darkColorScheme(
    primary          = RenkVurgu,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFF3B1D8A),
    secondary        = RenkVurgucAcik,
    background       = RenkArkaPlan,
    surface          = RenkYuzey,
    surfaceVariant   = RenkYuzeyYuksek,
    onBackground     = RenkMetin,
    onSurface        = RenkMetin,
    onSurfaceVariant = RenkMetinIkincil,
    error            = RenkKritik
)

@Composable
fun ProzessorTema(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ProzessorKoyuTema,
        content = content
    )
}
