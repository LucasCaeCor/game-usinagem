package br.com.usinagemmaster.feature.contracts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.ScreenHeader
import br.com.usinagemmaster.core.util.Formatters
import br.com.usinagemmaster.data.local.entity.ContractEntity
import br.com.usinagemmaster.domain.model.ContractStatus

private enum class ContractFilter(val label: String) {
    ALL("Todos"), AVAILABLE("Disponíveis"), ACTIVE("Ativos"), COMPLETED("Concluídos"), FAILED("Falharam")
}

@Composable
fun ContractsScreen(vm: ContractsViewModel = hiltViewModel()) {
    val contracts by vm.contracts.collectAsState()
    val message by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    var filter by remember { mutableStateOf(ContractFilter.ALL) }
    var cancelTarget by remember { mutableStateOf<ContractEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ContractEntity?>(null) }

    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.clearMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ScreenHeader("Contratos", "Aceite, acompanhe, cancele ou limpe o histórico")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ContractFilter.entries.forEach { item ->
                    val count = contracts.count { c -> item == ContractFilter.ALL || c.status == item.name }
                    FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text("${item.label} $count") })
                }
            }
            val visible = contracts.filter { filter == ContractFilter.ALL || it.status == filter.name }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (visible.isEmpty()) item { Text("Nenhum contrato nesta categoria.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(visible, key = { it.id }) { c ->
                    ContractCardV5(c, vm, onCancel = { cancelTarget = c }, onDelete = { deleteTarget = c })
                }
            }
        }
    }

    cancelTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Cancelar contrato?", fontWeight = FontWeight.Black) },
            text = { Text("O cliente ${c.clientName} aplicará multa de ${Formatters.money(c.penaltyCents)} e você perderá ${c.reputationPenalty} ponto(s) de reputação. Essa ação não pode ser desfeita.") },
            confirmButton = { Button(onClick = { cancelTarget = null; vm.cancel(c) }) { Text("Pagar multa e cancelar") } },
            dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text("Manter contrato") } }
        )
    }
    deleteTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Excluir contrato com falha?") },
            text = { Text("Ele sairá da lista. A multa e a reputação já aplicadas não serão alteradas.") },
            confirmButton = { Button(onClick = { deleteTarget = null; vm.dismissFailed(c) }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Voltar") } }
        )
    }
}

@Composable
private fun ContractCardV5(c: ContractEntity, vm: ContractsViewModel, onCancel: () -> Unit, onDelete: () -> Unit) {
    val progress = if (c.quantity <= 0) 0f else (c.completedQuantity.toFloat() / c.quantity.toFloat()).coerceIn(0f, 1f)
    ElevatedCard {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(c.clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("${c.contractType} • dificuldade ${c.difficulty}", style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(onClick = {}, label = { Text(statusLabel(c.status)) })
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("Produção: ${c.completedQuantity}/${c.quantity} • qualidade mín. ${c.requiredQuality}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Prêmio ${Formatters.money(c.rewardCents)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Multa ${Formatters.money(c.penaltyCents)}", style = MaterialTheme.typography.labelMedium)
            }
            when (c.status) {
                ContractStatus.AVAILABLE.name -> Button(onClick = { vm.accept(c) }, modifier = Modifier.fillMaxWidth()) { Text("Aceitar contrato") }
                ContractStatus.ACTIVE.name -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.completedQuantity >= c.quantity) Button(onClick = { vm.complete(c) }, modifier = Modifier.weight(1f)) { Text("Concluir e receber") }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar • multa") }
                }
                ContractStatus.COMPLETED.name -> OutlinedButton(onClick = { vm.recoverReward(c) }, modifier = Modifier.fillMaxWidth()) { Text("Verificar / recuperar pagamento") }
                ContractStatus.FAILED.name -> Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Excluir FAILED") }
            }
        }
    }
}

private fun statusLabel(status: String) = when (status) {
    ContractStatus.AVAILABLE.name -> "DISPONÍVEL"
    ContractStatus.ACTIVE.name -> "ATIVO"
    ContractStatus.COMPLETED.name -> "CONCLUÍDO"
    ContractStatus.FAILED.name -> "FAILED"
    else -> status
}
