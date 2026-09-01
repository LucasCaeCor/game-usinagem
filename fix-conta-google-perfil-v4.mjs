#!/usr/bin/env node
/**
 * FIX V4 - PERFIL + LOGIN GOOGLE NA ABERTURA + VINCULO DO SAVE EXISTENTE
 *
 * O que faz:
 *  1) Coloca um gate de conta na abertura do app.
 *  2) Se nao houver Google conectado: mostra "Continuar com Google" + "Jogar sem conectar agora".
 *  3) Se o Firebase ja estiver autenticado mas o save local ainda nao estiver vinculado:
 *     mostra "Vincular meu progresso atual".
 *  4) Adiciona botao global "Perfil" por cima da UI principal, portanto o perfil nao fica escondido na roleta.
 *  5) Vincula o SAVE LOCAL EXISTENTE ao Firebase UID sem alterar/limpar Room.
 *  6) Registra o vinculo em SharedPreferences e tenta registrar metadados em Firestore.
 *  7) Mantem compatibilidade com ExpansionHub/GoogleAuthBridge da V3.
 *  8) Patch opcional das regras Firestore para player_accounts/{uid}.
 *
 * Uso:
 *   node fix-conta-google-perfil-v4.mjs
 *   node fix-conta-google-perfil-v4.mjs --dry-run
 *   node fix-conta-google-perfil-v4.mjs --restore
 */

import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.cwd();
const DRY = process.argv.includes('--dry-run');
const RESTORE = process.argv.includes('--restore');
const BACKUP_ROOT = path.join(ROOT, '.patch-backups', 'fix-conta-google-perfil-v4');

const rel = (...p) => path.join(ROOT, ...p);
const log = (m) => console.log(`[fix-v4] ${m}`);
const fail = (m) => { console.error(`\n[fix-v4] ERRO: ${m}\n`); process.exit(1); };

function exists(file) { return fs.existsSync(rel(file)); }
function read(file) { return fs.readFileSync(rel(file), 'utf8'); }
function ensureDir(file) { fs.mkdirSync(path.dirname(file), { recursive: true }); }
function backup(file) {
  const src = rel(file);
  if (!fs.existsSync(src)) return;
  const dst = path.join(BACKUP_ROOT, file);
  if (fs.existsSync(dst)) return;
  if (DRY) { log(`backup: ${file}`); return; }
  ensureDir(dst);
  fs.copyFileSync(src, dst);
}
function write(file, content) {
  backup(file);
  if (DRY) { log(`alteraria: ${file}`); return; }
  const dst = rel(file);
  ensureDir(dst);
  fs.writeFileSync(dst, content.replace(/\r\n/g, '\n'), 'utf8');
  log(`ok: ${file}`);
}
function patch(file, fn) {
  if (!exists(file)) fail(`Arquivo nao encontrado: ${file}`);
  const before = read(file);
  const after = fn(before);
  if (after === before) { log(`sem alteracao: ${file}`); return; }
  write(file, after);
}
function addImport(src, line) {
  if (src.includes(line)) return src;
  const pkg = src.match(/^package\s+[^\n]+\n/m);
  if (!pkg) fail('Arquivo Kotlin sem package valido.');
  return src.slice(0, pkg.index + pkg[0].length) + `\n${line}\n` + src.slice(pkg.index + pkg[0].length);
}
function restore() {
  if (!fs.existsSync(BACKUP_ROOT)) fail('Nenhum backup da V4 encontrado.');
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else {
        const relative = path.relative(BACKUP_ROOT, full);
        const dst = rel(relative);
        ensureDir(dst);
        fs.copyFileSync(full, dst);
        log(`restaurado: ${relative}`);
      }
    }
  };
  walk(BACKUP_ROOT);
  log('Restauracao V4 concluida.');
}

// Scanner simples de chaves, ignorando strings e comentarios Kotlin o suficiente para localizar setContent.
function findMatchingBrace(src, openIndex) {
  let depth = 0;
  let string = null;
  let escape = false;
  let lineComment = false;
  let blockComment = false;
  for (let i = openIndex; i < src.length; i++) {
    const c = src[i], n = src[i + 1];
    if (lineComment) { if (c === '\n') lineComment = false; continue; }
    if (blockComment) { if (c === '*' && n === '/') { blockComment = false; i++; } continue; }
    if (string) {
      if (escape) { escape = false; continue; }
      if (c === '\\') { escape = true; continue; }
      if (c === string) string = null;
      continue;
    }
    if (c === '/' && n === '/') { lineComment = true; i++; continue; }
    if (c === '/' && n === '*') { blockComment = true; i++; continue; }
    if (c === '"' || c === '\'') { string = c; continue; }
    if (c === '{') depth++;
    else if (c === '}') {
      depth--;
      if (depth === 0) return i;
    }
  }
  return -1;
}

const ACCOUNT_STORE = `package br.com.usinagemmaster.feature.account

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class AccountLinkState(
    val localSaveId: String,
    val linkedUid: String?,
    val linkedEmail: String?,
    val linkedName: String?,
    val linkedAt: Long,
) {
    fun isLinkedTo(user: FirebaseUser?): Boolean = user != null && linkedUid == user.uid
    val isLinked: Boolean get() = !linkedUid.isNullOrBlank()
}

data class AccountLinkResult(
    val state: AccountLinkState,
    val cloudRegistryUpdated: Boolean,
)

/**
 * Vinculo de identidade do jogo.
 * IMPORTANTE: nao toca no banco Room. O save existente continua exatamente no aparelho.
 * A conta Google passa a ser a identidade dona deste save local.
 */
object AccountLinkStore {
    private const val PREFS = "usinagem_account_link_v1"
    private const val KEY_LOCAL_SAVE_ID = "local_save_id"
    private const val KEY_UID = "linked_google_uid"
    private const val KEY_EMAIL = "linked_google_email"
    private const val KEY_NAME = "linked_google_name"
    private const val KEY_LINKED_AT = "linked_at"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(context: Context): AccountLinkState {
        val p = prefs(context)
        var local = p.getString(KEY_LOCAL_SAVE_ID, null)
        if (local.isNullOrBlank()) {
            local = UUID.randomUUID().toString()
            p.edit().putString(KEY_LOCAL_SAVE_ID, local).apply()
        }
        return AccountLinkState(
            localSaveId = local,
            linkedUid = p.getString(KEY_UID, null),
            linkedEmail = p.getString(KEY_EMAIL, null),
            linkedName = p.getString(KEY_NAME, null),
            linkedAt = p.getLong(KEY_LINKED_AT, 0L),
        )
    }

    suspend fun linkCurrentProgress(context: Context, user: FirebaseUser): AccountLinkResult {
        val before = state(context)
        if (before.linkedUid != null && before.linkedUid != user.uid) {
            error("Este progresso já está vinculado a outra conta Google. Entre com a conta originalmente vinculada.")
        }

        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putString(KEY_UID, user.uid)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_NAME, user.displayName)
            .putLong(KEY_LINKED_AT, now)
            .apply()

        val after = state(context)
        val cloudOk = runCatching {
            FirebaseFirestore.getInstance()
                .collection("player_accounts")
                .document(user.uid)
                .set(
                    mapOf(
                        "uid" to user.uid,
                        "email" to user.email,
                        "displayName" to user.displayName,
                        "localSaveId" to after.localSaveId,
                        "provider" to "google",
                        "lastLinkedAt" to FieldValue.serverTimestamp(),
                        "clientLinkedAtMs" to now,
                    ),
                    SetOptions.merge(),
                )
                .await()
        }.isSuccess

        return AccountLinkResult(after, cloudOk)
    }

    suspend fun retryCloudRegistry(context: Context, user: FirebaseUser): Boolean {
        val s = state(context)
        if (!s.isLinkedTo(user)) return false
        return runCatching {
            FirebaseFirestore.getInstance().collection("player_accounts").document(user.uid)
                .set(
                    mapOf(
                        "uid" to user.uid,
                        "email" to user.email,
                        "displayName" to user.displayName,
                        "localSaveId" to s.localSaveId,
                        "provider" to "google",
                        "lastLinkedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                ).await()
        }.isSuccess
    }
}
`;

const AUTH = `package br.com.usinagemmaster.feature.expansion

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object GoogleAuthBridge {
    suspend fun signInUser(context: Context): FirebaseUser {
        require(FirebaseApp.getApps(context).isNotEmpty()) {
            "Firebase não inicializado. Confirme app/google-services.json e o plugin com.google.gms.google-services."
        }
        val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        require(resourceId != 0) {
            "default_web_client_id não encontrado. Baixe novamente o google-services.json após ativar Google no Firebase."
        }
        val serverClientId = context.getString(resourceId).trim()
        require(serverClientId.isNotBlank()) { "Web Client ID do Google está vazio." }

        val manager = CredentialManager.create(context)
        val credential = try {
            manager.getCredential(context, request(serverClientId, true)).credential
        } catch (_: NoCredentialException) {
            manager.getCredential(context, request(serverClientId, false)).credential
        } catch (first: Exception) {
            // Alguns aparelhos/emuladores retornam erro no filtro de contas autorizadas mesmo com
            // Firebase/OAuth corretos. A segunda tentativa abre todas as contas disponíveis.
            manager.getCredential(context, request(serverClientId, false)).credential
        }

        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "O Google retornou uma credencial inesperada."
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
        val auth = FirebaseAuth.getInstance()
        val current = auth.currentUser

        // Se o app já possuía uma identidade Firebase (ex.: anônima/outro provedor),
        // vincula Google à MESMA UID em vez de trocar de conta e perder associação remota.
        if (current != null && current.providerData.none { it.providerId == "google.com" }) {
            val linked = runCatching { current.linkWithCredential(firebaseCredential).await().user }.getOrNull()
            if (linked != null) return linked
        }

        return auth.signInWithCredential(firebaseCredential).await().user
            ?: error("Firebase autenticou, mas não retornou usuário.")
    }

    // Mantem compatibilidade com as telas V2/V3 que esperam String.
    suspend fun signIn(context: Context): String {
        val user = signInUser(context)
        return user.displayName ?: user.email ?: "Jogador"
    }

    private fun request(serverClientId: String, authorizedOnly: Boolean): GetCredentialRequest {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()
        return GetCredentialRequest.Builder().addCredentialOption(option).build()
    }

    fun currentUser(): FirebaseUser? = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
    fun signOut() { FirebaseAuth.getInstance().signOut() }
}
`;

const ACCOUNT_UI = `package br.com.usinagemmaster.feature.account

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

@Composable
fun AccountRootOverlay(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var authUser by remember { mutableStateOf<FirebaseUser?>(GoogleAuthBridge.currentUser()) }
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
            authUser = firebaseAuth.currentUser
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
        val user = authUser ?: return loginAndLink(closeAfter)
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
                val user = authUser
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
                Text("Usinagem Master", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (user == null) "Conecte sua conta Google para identificar e proteger seu progresso."
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
`;

function patchMainActivity(src) {
  if (src.includes('AccountRootOverlay { // FIX_V4_ACCOUNT_ROOT')) return src;
  let out = addImport(src, 'import br.com.usinagemmaster.feature.account.AccountRootOverlay');

  const m = /\bsetContent\s*\{/.exec(out);
  if (!m) fail('Nao encontrei setContent { em MainActivity.kt. Envie o arquivo se sua MainActivity usa outra estrutura.');
  const open = out.indexOf('{', m.index);
  const close = findMatchingBrace(out, open);
  if (close < 0) fail('Nao consegui localizar o fechamento de setContent { em MainActivity.kt.');

  // Inserimos wrapper dentro do setContent existente, preservando Theme/NavHost e toda a UI atual.
  out = out.slice(0, open + 1) + '\n            AccountRootOverlay { // FIX_V4_ACCOUNT_ROOT\n' + out.slice(open + 1, close) + '\n            } // FIX_V4_ACCOUNT_ROOT_END\n        ' + out.slice(close);
  return out;
}

function patchGradle(src) {
  const deps = [
    'implementation("androidx.credentials:credentials:1.3.0")',
    'implementation("androidx.credentials:credentials-play-services-auth:1.3.0")',
    'implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")',
    'implementation("com.google.firebase:firebase-auth")',
    'implementation("com.google.firebase:firebase-firestore")',
  ];
  const missing = deps.filter((d) => !src.includes(d));
  if (!missing.length) return src;
  const m = /dependencies\s*\{/.exec(src);
  if (!m) fail('Nao achei dependencies { em app/build.gradle.kts.');
  const open = src.indexOf('{', m.index);
  const close = findMatchingBrace(src, open);
  if (close < 0) fail('Nao consegui fechar dependencies { no Gradle.');
  return src.slice(0, close) + `\n    // FIX V4 - conta Google e vinculo do save\n    ${missing.join('\n    ')}\n` + src.slice(close);
}

function patchManifest(src) {
  if (src.includes('android.permission.INTERNET')) return src;
  return src.replace(/(<manifest[^>]*>)/, '$1\n    <uses-permission android:name="android.permission.INTERNET" />');
}

function patchRules(src) {
  if (src.includes('match /player_accounts/{uid}')) return src;
  const marker = 'match /databases/{database}/documents';
  const idx = src.indexOf(marker);
  if (idx < 0) return src;
  const open = src.indexOf('{', idx + marker.length);
  if (open < 0) return src;
  const close = findMatchingBrace(src, open);
  if (close < 0) return src;
  const rule = `\n\n    // FIX V4 - cada usuario pode registrar apenas o proprio vinculo de conta/save.\n    match /player_accounts/{uid} {\n      allow read, create, update: if request.auth != null && request.auth.uid == uid;\n      allow delete: if false;\n    }\n`;
  return src.slice(0, close) + rule + src.slice(close);
}

function diagnoseGoogleServices() {
  const f = 'app/google-services.json';
  if (!exists(f)) { log('AVISO: app/google-services.json nao encontrado.'); return; }
  try {
    const json = JSON.parse(read(f));
    const clients = Array.isArray(json.client) ? json.client : [];
    const androidClient = clients.find((c) => c?.client_info?.android_client_info?.package_name === 'br.com.usinagemmaster');
    if (!androidClient) return log('AVISO: JSON nao possui cliente br.com.usinagemmaster.');
    const oauth = Array.isArray(androidClient.oauth_client) ? androidClient.oauth_client : [];
    const web = oauth.find((o) => Number(o?.client_type) === 3);
    log(web ? 'Google Services: cliente Android + OAuth WEB encontrados.' : 'AVISO: OAuth WEB client_type 3 nao encontrado no JSON.');
  } catch (e) { log(`AVISO ao ler google-services.json: ${e.message}`); }
}

function main() {
  if (RESTORE) return restore();
  if (!exists('app/build.gradle.kts')) fail('Execute este arquivo na RAIZ do projeto.');
  if (!exists('app/src/main/java/br/com/usinagemmaster/MainActivity.kt')) fail('MainActivity.kt nao encontrada no caminho esperado.');
  if (!exists('app/src/main/java/br/com/usinagemmaster/feature/expansion/GoogleAuthBridge.kt')) {
    fail('GoogleAuthBridge da V3 nao foi encontrado. Aplique primeiro a V3 que voce ja esta usando.');
  }

  log(DRY ? 'MODO DRY-RUN' : 'Aplicando Perfil/Login/Vinculo V4...');

  write('app/src/main/java/br/com/usinagemmaster/feature/account/AccountLinkStore.kt', ACCOUNT_STORE);
  write('app/src/main/java/br/com/usinagemmaster/feature/account/AccountRootOverlay.kt', ACCOUNT_UI);
  write('app/src/main/java/br/com/usinagemmaster/feature/expansion/GoogleAuthBridge.kt', AUTH);
  patch('app/src/main/java/br/com/usinagemmaster/MainActivity.kt', patchMainActivity);
  patch('app/build.gradle.kts', patchGradle);
  const manifest = 'app/src/main/AndroidManifest.xml';
  if (exists(manifest)) patch(manifest, patchManifest);

  for (const rules of ['firestore.rules', 'firebase/firestore.rules']) {
    if (exists(rules)) patch(rules, patchRules);
  }

  diagnoseGoogleServices();

  log('');
  log('V4 aplicada. Resultado esperado:');
  log('  1) Na ABERTURA aparece Continuar com Google ou Vincular meu progresso atual.');
  log('  2) Jogador pode escolher Jogar sem conectar agora.');
  log('  3) Botao global 👤 Perfil fica visivel fora da roleta.');
  log('  4) Vincular preserva TODO o save Room existente; nao cria empresa nova.');
  log('  5) Firebase UID fica associado ao localSaveId e tenta registrar player_accounts/{uid}.');
  log('');
  log('IMPORTANTE: se as regras Firestore foram alteradas, publique firestore.rules no Firebase Console/CLI.');
  log('Depois rode: .\\gradlew.bat clean assembleDebug');
}

main();
