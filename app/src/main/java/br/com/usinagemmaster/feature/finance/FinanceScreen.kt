package br.com.usinagemmaster.feature.finance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.ScreenHeader
import br.com.usinagemmaster.core.util.Formatters

@Composable
fun FinanceScreen(vm: FinanceViewModel = hiltViewModel()) {
    val transactions by vm.transactions.collectAsState()

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        ScreenHeader("Relatório Financeiro", "Últimas movimentações do caixa")
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(transactions, key = { it.id }) { transaction ->
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(transaction.description, fontWeight = FontWeight.Bold)
                            Text(transaction.category, style = MaterialTheme.typography.bodySmall)
                        }
                        val isIncome = transaction.type == "INCOME"
                        Text(
                            (if (isIncome) "+" else "-") + Formatters.money(transaction.amountCents),
                            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
