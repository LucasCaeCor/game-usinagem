package br.com.usinagemmaster.feature.expansion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// V12_HOME_MENU
private data class AdvancedHomeAction(
    val section: String,
    val icon: String,
    val title: String,
    val subtitle: String,
)

private val advancedHomeActions = listOf(
    AdvancedHomeAction("gacha", "🎰", "Roleta", "Gacha industrial e pity"),
    AdvancedHomeAction("company", "🏭", "Empresa", "Especialidade e máquinas TOP"),
    AdvancedHomeAction("skills", "🔬", "Pesquisa", "Árvore de skills e evolução"),
    AdvancedHomeAction("tools", "🧰", "Ferramentas", "Inventário e ferramentas por contrato"),
    AdvancedHomeAction("character", "👷", "Personagens", "Equipe especial, skins e bônus"),
    AdvancedHomeAction("market", "🌐", "Mercado", "Ofertar personagem • contratar 48h"),
    AdvancedHomeAction("contracts", "📚", "Histórico", "Contratos concluídos e registros"),
)

@Composable
fun ExpansionHomeMenu() {
    var openedSection by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Sistemas da empresa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(
                "atalhos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        advancedHomeActions.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { action ->
                    Card(
                        onClick = { openedSection = action.section },
                        modifier = Modifier.weight(1f).heightIn(min = 118.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = Color.White,
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = .35f)
                        ),
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(action.icon, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                action.title,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Text(
                                action.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    openedSection?.let { section ->
        ExpansionHubDialog(
            onDismiss = { openedSection = null },
            initialSection = section,
            showSectionNavigation = false,
        )
    }
}
