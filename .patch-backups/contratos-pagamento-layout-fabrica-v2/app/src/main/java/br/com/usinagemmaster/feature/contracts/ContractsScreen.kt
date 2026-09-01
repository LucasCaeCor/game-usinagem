package br.com.usinagemmaster.feature.contracts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.ScreenHeader
import br.com.usinagemmaster.core.designsystem.component.StatusPill
import br.com.usinagemmaster.core.util.Formatters
import br.com.usinagemmaster.domain.model.ContractStatus

@Composable
fun ContractsScreen(vm: ContractsViewModel = hiltViewModel()) {
    val list by vm.contracts.collectAsState()
    val message by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { padding ->
        Column(Modifier.padding(padding)) {
            ScreenHeader("Contratos", "Aceite serviços; a produção das máquinas avança os lotes automaticamente")
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.id }) { contract ->
                    val progress = if (contract.quantity == 0) 0f else contract.completedQuantity.toFloat() / contract.quantity
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(contract.clientName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                StatusPill(
                                    when (contract.status) {
                                        ContractStatus.AVAILABLE.name -> "DISPONÍVEL"
                                        ContractStatus.ACTIVE.name -> "EM PRODUÇÃO"
                                        ContractStatus.COMPLETED.name -> "CONCLUÍDO"
                                        else -> contract.status
                                    },
                                    contract.status != ContractStatus.FAILED.name
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Text("${contract.contractType} • ${contract.quantity} peças • dificuldade ${contract.difficulty}")
                            Text("Qualidade mínima ${contract.requiredQuality}%", color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(10.dp))

                            if (contract.status == ContractStatus.ACTIVE.name || contract.status == ContractStatus.COMPLETED.name) {
                                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("Produção: ${contract.completedQuantity}/${contract.quantity} peças", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                            }

                            Text("Prêmio ${Formatters.money(contract.rewardCents)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            if (contract.status == ContractStatus.AVAILABLE.name) {
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { vm.accept(contract) }) { Text("Aceitar contrato") }
                            }
                        }
                    }
                }
            }
        }
    }
}
