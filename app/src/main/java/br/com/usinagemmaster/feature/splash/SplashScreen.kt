package br.com.usinagemmaster.feature.splash

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.IndustrialBackground
import br.com.usinagemmaster.core.util.Formatters
import java.util.Locale
import androidx.compose.ui.graphics.Color

@Composable
fun SplashScreen(vm: SplashViewModel = hiltViewModel(), onContinue: () -> Unit) {
    val state by vm.state.collectAsState()
    IndustrialBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.PrecisionManufacturing, null, Modifier.size(86.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("USINAGEM MASTER", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
                Text("Império do Aço", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(36.dp))
                if (state.loading) {
                    CircularProgressIndicator()
                } else {
                    state.offlineReport?.let { report ->
                        Card {
                            Column(Modifier.padding(18.dp)) {
                                Text("A fábrica continuou evoluindo", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("Tempo calculado: ${Formatters.duration(report.minutes)}")
                                Text("Produção: ${String.format(Locale.getDefault(), "%.1f", report.producedUnits)} peças")
                                Text("Resultado: +${Formatters.money(report.earnedCents)}")
                                if (report.completedContracts > 0) {
                                    Text("Contratos concluídos: ${report.completedContracts}", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Entrar na oficina") }
                }
            }
        }
    }
}
