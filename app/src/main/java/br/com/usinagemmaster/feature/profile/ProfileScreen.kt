package br.com.usinagemmaster.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
                SkinGallery(
                    avatar = draft.avatar,
                    selected = draft.avatar.skinStyle
                ) { selected ->
                    draft = draft.withAvatar {
                        copy(
                            skinStyle = selected,
                            gender = if (selected == "PRINCESA") "FEMALE" else gender,
                            hairStyle = if (selected == "PRINCESA") "LONG" else hairStyle
                        )
                    }
                }
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
private fun SkinGallery(avatar: PlayerAvatar, selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        Triple("WORKSHOP", "Operário", "Visual clássico de chão de fábrica"),
        Triple("TATUZAO", "Tatuzão", "Grande, pesado e cheio de presença"),
        Triple("PRINCESA", "Princesa", "Vestido, coroa e cabelo longo em camadas"),
        Triple("PINOQUIO", "Pinóquio", "Macacão e nariz exagerado"),
        Triple("MAGRAO", "Magrão e alto", "Silhueta longa, fina e passos largos"),
        Triple("KENDAO_KIMONO", "Kendão", "Kimono industrial, faixa e mangas largas"),
        Triple("TREME_TREME", "Treme-treme", "Movimento nervoso e identidade própria"),
        Triple("BEBADO", "Bêbado", "Postura torta e balanço cômico")
    )
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Skin", fontWeight = FontWeight.ExtraBold)
                Text("Escolha pela aparência: cada skin muda roupa, silhueta e animação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AssistChip(onClick = {}, label = { Text("SPRITE PREMIUM") })
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            options.forEach { (value, label, description) ->
                val active = selected == value
                Card(
                    onClick = { onSelect(value) },
                    modifier = Modifier.width(142.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f)),
                    colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha=.32f) else MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlayerAvatarPreview(
                            avatar = avatar.copy(
                                skinStyle = value,
                                gender = if (value == "PRINCESA") "FEMALE" else avatar.gender,
                                hairStyle = if (value == "PRINCESA") "LONG" else avatar.hairStyle
                            ),
                            size = 88.dp
                        )
                        Text(label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2)
                        if (active) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                                Text("Selecionada", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("Toque para usar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
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
