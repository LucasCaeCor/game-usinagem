package br.com.usinagemmaster.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.usinagemmaster.feature.expansion.GoogleAuthBridge
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

@Composable
fun AccountRootOverlay(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var authUser by remember { mutableStateOf<FirebaseUser?>(GoogleAuthBridge.currentGoogleUser()) }
    var linkState by remember { mutableStateOf(AccountLinkStore.state(context)) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showStartup by rememberSaveable {
        mutableStateOf(authUser == null || !linkState.isLinkedTo(authUser))
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            authUser = firebaseAuth.currentUser?.takeIf { GoogleAuthBridge.isGoogleUser(it) }
            linkState = AccountLinkStore.state(context)
        }
        auth?.addAuthStateListener(listener)
        onDispose { auth?.removeAuthStateListener(listener) }
    }

    fun loginAndLink(closeAfter: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            message = null
            runCatching {
                val user = GoogleAuthBridge.signInUser(context)
                authUser = user
                val result = AccountLinkStore.linkCurrentProgress(context, user)
                linkState = result.state
                if (result.cloudRegistryUpdated) {
                    "Conta Google conectada e progresso atual vinculado."
                } else {
                    "Conta Google conectada e save preservado. O registro no Firestore ficou pendente; publique as regras da V4."
                }
            }.onSuccess {
                message = it
                if (closeAfter) showStartup = false
            }.onFailure { message = it.message ?: "Não foi possível entrar com Google." }
            busy = false
        }
    }

    fun linkSignedUser(closeAfter: Boolean) {
        val user = authUser?.takeIf { GoogleAuthBridge.isGoogleUser(it) } ?: return loginAndLink(closeAfter)
        if (busy) return
        scope.launch {
            busy = true
            message = null
            runCatching { AccountLinkStore.linkCurrentProgress(context, user) }
                .onSuccess {
                    linkState = it.state
                    message = if (it.cloudRegistryUpdated) "Progresso atual vinculado à conta Google." else "Progresso vinculado localmente; registro Firestore pendente."
                    if (closeAfter) showStartup = false
                }
                .onFailure { message = it.message ?: "Falha ao vincular progresso." }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()

        // Perfil GLOBAL: nao depende de Fábrica, Roleta ou Centro de Evolução.
        ElevatedButton(
            onClick = { showProfile = true; message = null },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 6.dp, end = 8.dp)
                .shadow(8.dp, RoundedCornerShape(50)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("👤 Perfil", fontWeight = FontWeight.Bold)
        }
    }

    if (showStartup) {
        StartupAccountDialog(
            user = authUser,
            linked = linkState.isLinkedTo(authUser),
            busy = busy,
            message = message,
            onGoogle = { loginAndLink(true) },
            onLink = { linkSignedUser(true) },
            onSkip = { showStartup = false },
        )
    }

    if (showProfile) {
        ProfileAccountDialog(
            user = authUser,
            linkState = linkState,
            busy = busy,
            message = message,
            onDismiss = { showProfile = false },
            onGoogle = { loginAndLink(false) },
            onLink = { linkSignedUser(false) },
            onRetryCloud = {
                val user = authUser?.takeIf { GoogleAuthBridge.isGoogleUser(it) }
                if (user == null) {
                    message = "Entre com Google antes de sincronizar."
                } else {
                    scope.launch {
                        busy = true
                        val ok = AccountLinkStore.retryCloudRegistry(context, user)
                        message = if (ok) "Registro da conta sincronizado no Firestore." else "Ainda não foi possível registrar no Firestore. Confira/publice as regras da V4."
                        busy = false
                    }
                }
            },
            onSignOut = {
                GoogleAuthBridge.signOut()
                authUser = null
                message = "Google desconectado. Seu save local continua intacto e vinculado à conta anterior."
            },
        )
    }
}

@Composable
private fun StartupAccountDialog(
    user: FirebaseUser?,
    linked: Boolean,
    busy: Boolean,
    message: String?,
    onGoogle: () -> Unit,
    onLink: () -> Unit,
    onSkip: () -> Unit,
) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 26.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("⚙", Modifier.padding(22.dp), style = MaterialTheme.typography.displaySmall)
                }
                Spacer(Modifier.height(18.dp))
                Text("Usinagem Master", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (user == null) "Conecte uma conta Google real. Se o jogo já criou uma UID temporária, ela será vinculada ao Google sem apagar seu progresso."
                    else "Sua conta Google foi encontrada. Vincule a empresa que já existe neste aparelho.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(20.dp))

                if (user != null) {
                    AccountIdentityCard(user)
                    Spacer(Modifier.height(12.dp))
                }

                if (message != null) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(message, Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = if (user == null) onGoogle else onLink,
                    enabled = !busy && !linked,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (user == null) "G  Continuar com Google" else if (linked) "✓ Progresso já vinculado" else "Vincular meu progresso atual")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "O vínculo NÃO cria uma empresa nova e NÃO apaga seu save atual.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onSkip, enabled = !busy) { Text("Jogar sem conectar agora") }
            }
        }
    }
}

@Composable
private fun ProfileAccountDialog(
    user: FirebaseUser?,
    linkState: AccountLinkState,
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onGoogle: () -> Unit,
    onLink: () -> Unit,
    onRetryCloud: () -> Unit,
    onSignOut: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Meu Perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text("Conta e vínculo do progresso", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDismiss) { Text("Fechar") }
                }
                HorizontalDivider()
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (user == null) {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Conta Google", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Você está jogando com o progresso local deste aparelho.")
                                Button(onClick = onGoogle, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text("G  Entrar com Google e vincular")
                                }
                            }
                        }
                    } else {
                        AccountIdentityCard(user)
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Progresso do jogo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("ID local: …" + linkState.localSaveId.takeLast(8))
                                if (linkState.isLinkedTo(user)) {
                                    Text("✓ Este save está vinculado a esta conta Google", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text("Seu banco Room não foi recriado: dinheiro, nível, máquinas, contratos, skins e gacha permanecem no save atual.")
                                    OutlinedButton(onClick = onRetryCloud, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sincronizar registro da conta") }
                                } else if (linkState.isLinked) {
                                    Text("⚠ Este save foi vinculado anteriormente a outra conta Google.", color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("Este save ainda não está associado a uma conta Google.")
                                    Button(onClick = onLink, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Vincular progresso atual") }
                                }
                            }
                        }
                        OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Desconectar Google deste aparelho")
                        }
                    }

                    message?.let {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                            Text(it, Modifier.fillMaxWidth().padding(12.dp))
                        }
                    }

                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Como funciona", fontWeight = FontWeight.Bold)
                            Text("• A empresa existente continua sendo a mesma.")
                            Text("• Entrar com Google não cria um save vazio por cima dela.")
                            Text("• O vínculo usa o UID do Firebase como identidade da conta.")
                            Text("• A V4 também tenta registrar localSaveId em player_accounts no Firestore.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountIdentityCard(user: FirebaseUser) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text("G", Modifier.padding(horizontal = 15.dp, vertical = 10.dp), fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text(user.displayName ?: "Conta Google", fontWeight = FontWeight.Bold)
                Text(user.email ?: "UID " + user.uid.take(8), style = MaterialTheme.typography.bodySmall)
            }
            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
        }
    }
}
