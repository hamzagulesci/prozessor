package com.hamza.prozessor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.hamza.prozessor.MainViewModel

// ─── Navigasyon Rotaları ──────────────────────────────────────────────────────
sealed class Rota(val yol: String, val baslik: String) {
    object ProcessListe  : Rota("process",  "Süreçler")
    object RamPanel      : Rota("ram",      "RAM")
    object OffenderListe : Rota("offender", "Tehditler")
    object Ayarlar       : Rota("settings", "Ayarlar")
}

/**
 * Uygulamanın ana navigasyon ekranı.
 * Alt navigasyon çubuğu ile 4 sekme arasında geçiş yapar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val uiDurum by viewModel.uiDurum.collectAsState()

    val sekmeler = listOf(
        Triple(Rota.ProcessListe,  Icons.Default.Memory,     "Süreçler"),
        Triple(Rota.RamPanel,      Icons.Default.BarChart,   "RAM"),
        Triple(Rota.OffenderListe, Icons.Default.Security,   "Tehditler"),
        Triple(Rota.Ayarlar,       Icons.Default.Settings,   "Ayarlar")
    )

    // Eylem sonucu snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiDurum.sonEylemSonucu) {
        uiDurum.sonEylemSonucu?.let { mesaj ->
            snackbarHostState.showSnackbar(mesaj, duration = SnackbarDuration.Short)
            viewModel.eylemSonucunuTemizle()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val mevcutHedef by navController.currentBackStackEntryAsState()
                val mevcutRota = mevcutHedef?.destination?.route

                sekmeler.forEach { (rota, ikon, baslik) ->
                    NavigationBarItem(
                        selected = mevcutRota == rota.yol,
                        onClick = {
                            navController.navigate(rota.yol) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            // Tehditler sekmesinde aktif uyarı sayısını badge olarak göster
                            if (rota == Rota.OffenderListe && uiDurum.aktifUyarilar.isNotEmpty()) {
                                BadgedBox(badge = {
                                    Badge { Text(uiDurum.aktifUyarilar.size.toString()) }
                                }) {
                                    Icon(ikon, contentDescription = baslik)
                                }
                            } else {
                                Icon(ikon, contentDescription = baslik)
                            }
                        },
                        label = { Text(baslik, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { ic ->
        // Shizuku bağlı değilse üst banner göster
        Column(modifier = Modifier.padding(ic)) {
            if (!uiDurum.shizukuBagli) {
                ShizukuUyariBanneri()
            }

            NavHost(
                navController = navController,
                startDestination = Rota.ProcessListe.yol
            ) {
                composable(Rota.ProcessListe.yol) {
                    ProcessListeEkrani(viewModel = viewModel)
                }
                composable(Rota.RamPanel.yol) {
                    RamPanelEkrani(viewModel = viewModel)
                }
                composable(Rota.OffenderListe.yol) {
                    TehditListesiEkrani(viewModel = viewModel)
                }
                composable(Rota.Ayarlar.yol) {
                    AyarlarEkrani(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun ShizukuUyariBanneri() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.small
            )
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(MaterialTheme.spacing.small))
            Text(
                text = "Shizuku bağlı değil — eylemler çalışmaz. Kurulum için Ayarlar sekmesini aç.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// Spacing extension (pratik kullanım için)
object Spacing {
    val extraSmall = androidx.compose.ui.unit.dp * 4
    val small = androidx.compose.ui.unit.dp * 8
    val medium = androidx.compose.ui.unit.dp * 16
    val large = androidx.compose.ui.unit.dp * 24
    val extraLarge = androidx.compose.ui.unit.dp * 32
}

val MaterialTheme.spacing: Spacing get() = Spacing
