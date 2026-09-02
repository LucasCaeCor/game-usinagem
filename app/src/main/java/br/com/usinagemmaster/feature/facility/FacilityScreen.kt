package br.com.usinagemmaster.feature.facility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import java.text.NumberFormat
import java.util.Locale

@Composable
fun FacilityScreen(vm: FacilityViewModel = hiltViewModel()) {
    val s by vm.dashboard.collectAsState()
    val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // Mantém exatamente a mesma fórmula usada pelo GameRepositoryImpl.upgradeWarehouse().
    val currentWarehouseLevel = (((s.warehouseSpace - 100).coerceAtLeast(0)) / 50) + 1
    val nextWarehouseLevel = currentWarehouseLevel + 1
    val nextSpace = s.warehouseSpace + 50
    val nextCostCents = 2_000_000L * currentWarehouseLevel.toLong()
    val canAfford = s.cashCents >= nextCostCents
    val remainingAfterPurchase = (s.cashCents - nextCostCents).coerceAtLeast(0L)
    val missingCents = (nextCostCents - s.cashCents).coerceAtLeast(0L)
    val occupationPct = if (s.warehouseSpace <= 0) 0
        else ((s.usedWarehouseSpace.toDouble() / s.warehouseSpace.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp)
        ) {
            ScreenHeader(
                "Reforma e Expansão",
                "Veja o custo e o impacto antes de investir"
            )

            ElevatedCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "🏭 Expansão do galpão",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "NÍVEL ATUAL",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                currentWarehouseLevel.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column {
                            Text(
                                "PRÓXIMO NÍVEL",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                nextWarehouseLevel.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        "Espaço",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${s.warehouseSpace} m²  →  ${nextSpace} m²",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Uso atual: ${s.usedWarehouseSpace} / ${s.warehouseSpace} m² (${occupationPct}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    Text(
                        "PREÇO DA PRÓXIMA EXPANSÃO",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        moneyV9(nextCostCents),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = if (canAfford) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )

                    Text(
                        "Seu saldo: ${moneyV9(s.cashCents)}",
                        fontWeight = FontWeight.SemiBold
                    )

                    if (canAfford) {
                        Text(
                            "Após a compra: ${moneyV9(remainingAfterPurchase)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Faltam ${moneyV9(missingCents)} para expandir.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    Button(
                        onClick = vm::expand,
                        enabled = canAfford,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Expandir +50 m² • ${moneyV9(nextCostCents)}")
                    }

                    Text(
                        "O valor aumenta a cada nível do galpão. A compra só é confirmada ao tocar no botão acima.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Como o preço é calculado?",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Cada expansão adiciona 50 m². O preço cresce conforme o nível atual do galpão, deixando as ampliações avançadas mais valiosas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                "Próximas melhorias: rede elétrica, iluminação, ventilação, ponte rolante e ar comprimido.",
                Modifier.padding(18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun moneyV9(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)
