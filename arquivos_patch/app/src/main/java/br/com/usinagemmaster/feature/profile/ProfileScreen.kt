package br.com.usinagemmaster.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.PlayerAvatarPreview
import br.com.usinagemmaster.domain.social.LocalPlayerProfile
import br.com.usinagemmaster.domain.social.PlayerAvatar

private data class AvatarOption(val value: String, val label: String)

@Composable
fun ProfileScreen(vm: ProfileViewModel = hiltViewModel()) {
    val saved by vm.profile.collectAsState()
    val message by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    var draft by remember { mutableStateOf(saved) }
    var seeded by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (!seeded || saved.onboardingComplete) {
            draft = saved
            seeded = true
        }
    }
    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121C22)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .24f))
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xFF1B2B33), Color(0xFF10171C))))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SEU PERSONAGEM", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(12.dp))
                        PlayerAvatarPreview(draft.avatar, size = 182.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(draft.displayName.ifBlank { "Dono da oficina" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text("Esse personagem também aparece na sua Fábrica Viva e pode circular pelo setor para fiscalizar a equipe.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = draft.displayName,
                    onValueChange = { draft = draft.copy(displayName = it.take(24)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome do personagem") },
                    leadingIcon = { Icon(Icons.Default.Badge, null) },
                    singleLine = true
                )
            }

            item { AvatarSection("Personagem", listOf(AvatarOption("MALE", "Masculino"), AvatarOption("FEMALE", "Feminino")), draft.avatar.gender) { draft = draft.withAvatar { copy(gender = it) } } }
            item {
                AvatarSection(
                    "Skin",
                    listOf(
                        AvatarOption("WORKSHOP", "Operário"),
                        AvatarOption("TATUZAO", "Tatuzão"),
                        AvatarOption("PRINCESA", "Princesa"),
                        AvatarOption("PINOQUIO", "Pinóquio"),
                        AvatarOption("MAGRAO", "Magrão alto"),
                        AvatarOption("KENDAO_KIMONO", "Kendão kimono"),
                        AvatarOption("TREME_TREME", "Treme-treme"),
                        AvatarOption("BEBADO", "Bêbado")
                    ),
                    draft.avatar.skinStyle
                ) { draft = draft.withAvatar { copy(skinStyle = it) } }
            }
            item { AvatarSection("Corpo", listOf(AvatarOption("SLIM", "Magro"), AvatarOption("STANDARD", "Padrão"), AvatarOption("STRONG", "Forte")), draft.avatar.bodyType) { draft = draft.withAvatar { copy(bodyType = it) } } }
            item { AvatarSection("Tom de pele", listOf(AvatarOption("LIGHT", "Claro"), AvatarOption("MEDIUM", "Médio"), AvatarOption("TAN", "Bronzeado"), AvatarOption("DARK", "Escuro")), draft.avatar.skinTone) { draft = draft.withAvatar { copy(skinTone = it) } } }
            item { AvatarSection("Cabelo", listOf(AvatarOption("SHORT", "Curto"), AvatarOption("BUZZ", "Raspado"), AvatarOption("MOHAWK", "Moicano"), AvatarOption("LONG", "Longo"), AvatarOption("PONYTAIL", "Rabo de cavalo"), AvatarOption("CURLY", "Cacheado"), AvatarOption("BALD", "Careca")), draft.avatar.hairStyle) { draft = draft.withAvatar { copy(hairStyle = it) } } }
            item { AvatarSection("Cor do cabelo", listOf(AvatarOption("DARK", "Preto"), AvatarOption("BROWN", "Castanho"), AvatarOption("BLONDE", "Loiro"), AvatarOption("GRAY", "Grisalho")), draft.avatar.hairColor) { draft = draft.withAvatar { copy(hairColor = it) } } }
            item { AvatarSection("Uniforme", listOf(AvatarOption("NAVY", "Marinho"), AvatarOption("BLUE", "Azul"), AvatarOption("GRAPHITE", "Grafite"), AvatarOption("GREEN", "Verde"), AvatarOption("ORANGE", "Laranja")), draft.avatar.uniformColor) { draft = draft.withAvatar { copy(uniformColor = it) } } }
            item { AvatarSection("Capacete", listOf(AvatarOption("YELLOW", "Amarelo"), AvatarOption("WHITE", "Branco"), AvatarOption("BLUE", "Azul"), AvatarOption("RED", "Vermelho"), AvatarOption("BLACK", "Preto")), draft.avatar.helmetColor) { draft = draft.withAvatar { copy(helmetColor = it) } } }
            item { AvatarSection("Acessório", listOf(AvatarOption("NONE", "Nenhum"), AvatarOption("GLASSES", "Óculos"), AvatarOption("HEADSET", "Headset")), draft.avatar.accessory) { draft = draft.withAvatar { copy(accessory = it) } } }

            item {
                Button(
                    onClick = { vm.save(draft) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Salvar personagem", fontWeight = FontWeight.Black)
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Engineering, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Seu avatar é salvo localmente. Com Firebase configurado, o mesmo perfil é publicado na Comunidade.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarSection(title: String, options: List<AvatarOption>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.ExtraBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option.value,
                    onClick = { onSelect(option.value) },
                    label = { Text(option.label) },
                    leadingIcon = if (selected == option.value) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
                )
            }
        }
    }
}

private inline fun LocalPlayerProfile.withAvatar(block: PlayerAvatar.() -> PlayerAvatar): LocalPlayerProfile = copy(avatar = avatar.block())
