package br.com.usinagemmaster.feature.store
import br.com.usinagemmaster.feature.expansion.PremiumCharacterStoreButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.ScreenHeader
import br.com.usinagemmaster.core.util.Formatters

@Composable
fun StoreScreen(vm: StoreViewModel = hiltViewModel()) {
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).padding(top = 20.dp)) {
        PremiumCharacterStoreButton()

            ScreenHeader("Loja de Máquinas", "Modernize sua oficina e aumente a produção")
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.catalog) { machine ->
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(machine.name, fontWeight = FontWeight.Bold)
                            Text("${String.format("%.1f", machine.baseProductionPerHour / 6.0)} un/10 min • Qualidade ${machine.quality} • ${machine.space} m²")
                            Text("Consumo ${machine.powerKw} kW", style = MaterialTheme.typography.bodySmall)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    Formatters.money(machine.priceCents),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(onClick = { vm.buy(machine.type.name) }) {
                                    Text("Comprar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
