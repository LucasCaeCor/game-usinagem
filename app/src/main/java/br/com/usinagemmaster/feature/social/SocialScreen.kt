package br.com.usinagemmaster.feature.social
import br.com.usinagemmaster.feature.community.CommunityFactoryButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.PlayerAvatarPreview
import br.com.usinagemmaster.domain.social.OnlinePlayer
import br.com.usinagemmaster.domain.social.SocialHelpGift
import kotlinx.coroutines.delay
import java.util.Locale
import br.com.usinagemmaster.feature.community.CommunityFactoryStableHost

@Composable
fun SocialScreen(
    onEditProfile: () -> Unit,
    vm: SocialViewModel = hiltViewModel()
) {
    val connection by vm.connection.collectAsState()
    val profile by vm.profile.collectAsState()
    val players by vm.players.collectAsState()
    val gifts by vm.gifts.collectAsState()
    val message by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.connect()
        while (true) {
            delay(30_000L)
            vm.refreshPresence()
        }
    }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.clearMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 34.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // V13_COMMUNITY_PERSISTENT_LAUNCHER
        item { CommunityFactoryButton() }

            item {
                Text("COMUNIDADE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                Text("Donos de oficina", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Multiplayer assíncrono: compare fábricas, acompanhe quem está online e envie apoio de produção.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }

            if (!connection.configured) {
                item { FirebaseSetupCard() }
            } else if (!profile.onboardingComplete) {
                item {
                    Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CommunityFactoryButton()

                            Text("Crie seu dono da fábrica", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Text("Antes de entrar na Comunidade, escolha nome, uniforme, capacete e aparência.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.PersonAdd, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Criar personagem")
                            }
                        }
                    }
                }
            } else {
                item {
                    MyOnlineProfileCard(
                        name = profile.displayName,
                        avatar = profile.avatar,
                        online = connection.connected,
                        onEdit = onEditProfile,
                        onPublish = { vm.publishPresence() }
                    )
                }

                if (gifts.isNotEmpty()) {
                    item {
                        Text("Apoios recebidos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    }
                    items(gifts, key = { it.id }) { gift -> GiftCard(gift, onClaim = { vm.claimHelp(gift) }) }
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Ranking da comunidade", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text("Ordenado por reputação", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(onClick = vm::connect, label = { Text(if (connection.connected) "Online" else "Reconectar") }, leadingIcon = { Icon(if (connection.connected) Icons.Default.Wifi else Icons.Default.WifiOff, null, Modifier.size(16.dp)) })
                    }
                }

                if (players.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Text("Ainda não encontrei outros jogadores. Quando outra instalação publicar um perfil, ela aparece aqui.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(players, key = { it.uid }) { player -> PlayerCard(player, onHelp = { vm.sendHelp(player) }) }
                }
            }
        }
    }

    // V13_COMMUNITY_STABLE_HOST_CALL
    CommunityFactoryStableHost()
}

@Composable
private fun MyOnlineProfileCard(
    name: String,
    avatar: br.com.usinagemmaster.domain.social.PlayerAvatar,
    online: Boolean,
    onEdit: () -> Unit,
    onPublish: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF142027)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .28f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatarPreview(avatar, size = 92.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text("●", color = if (online) Color(0xFF61DEA0) else Color(0xFFFFB45E))
                }
                Text(if (online) "Perfil publicado" else "Conectando ao Firebase", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onEdit) { Text("Editar") }
                    TextButton(onClick = onPublish) { Text("Atualizar") }
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(player: OnlinePlayer, onHelp: () -> Unit) {
    val online = System.currentTimeMillis() - player.lastSeenAt <= 15 * 60_000L
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatarPreview(player.avatar, size = 78.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(player.displayName, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("●", color = if (online) Color(0xFF61DEA0) else Color(0xFF718088), style = MaterialTheme.typography.labelSmall)
                }
                Text(player.companyName, style = MaterialTheme.typography.bodySmall, color = Color.White)
                Text("Nv. ${player.companyLevel} • Rep. ${player.reputation} • ${player.machineCount} máquinas", style = MaterialTheme.typography.labelSmall)
                Text(String.format(Locale.getDefault(), "%.1f pç / 10 min", player.productionPer10Minutes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            FilledTonalButton(onClick = onHelp, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 8.dp)) {
                Icon(Icons.Default.Bolt, null, Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Apoiar")
            }
        }
    }
}

@Composable
private fun GiftCard(gift: SocialHelpGift, onClaim: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF173126)), border = BorderStroke(1.dp, Color(0xFF61DEA0).copy(alpha = .32f))) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CardGiftcard, null, tint = Color(0xFF61DEA0))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(gift.fromName, fontWeight = FontWeight.ExtraBold)
                Text("enviou +10 min de apoio", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onClaim) { Text("Resgatar") }
        }
    }
}

@Composable
private fun FirebaseSetupCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2113)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .38f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Firebase ainda não conectado", fontWeight = FontWeight.Black)
            }
            Text("O modo offline continua funcionando normalmente. Para liberar a Comunidade:", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("1. Crie o app Android br.com.usinagemmaster no Firebase.\n2. Ative Authentication > Anonymous.\n3. Crie o Cloud Firestore.\n4. Coloque google-services.json dentro da pasta app/.\n5. Publique as regras firestore.rules incluídas no projeto.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
