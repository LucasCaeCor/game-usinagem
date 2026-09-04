#!/usr/bin/env node
/**
 * Usinagem Master — V25
 * Transferência segura de progresso entre vínculos Google.
 *
 * Patch cirúrgico: não depende do hash exato dos arquivos e não sobrescreve
 * o Perfil inteiro. Requer a infraestrutura Cloud Save da V23.
 *
 * Uso:
 *   node aplicar-usinagem-master-v25-transferir-progresso-google.mjs --check
 *   node aplicar-usinagem-master-v25-transferir-progresso-google.mjs
 *   node aplicar-usinagem-master-v25-transferir-progresso-google.mjs --build
 */

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';

const args = new Set(process.argv.slice(2));
const checkOnly = args.has('--check');
const runBuild = args.has('--build');
const root = process.cwd();
const linkRel = 'app/src/main/java/br/com/usinagemmaster/feature/account/AccountLinkStore.kt';
const overlayRel = 'app/src/main/java/br/com/usinagemmaster/feature/account/AccountRootOverlay.kt';
const cloudRel = 'app/src/main/java/br/com/usinagemmaster/data/cloud/CloudSaveRepository.kt';
const linkTarget = path.join(root, ...linkRel.split('/'));
const overlayTarget = path.join(root, ...overlayRel.split('/'));
const cloudTarget = path.join(root, ...cloudRel.split('/'));

function fail(message, code = 1) { console.error(`\n[V25] ERRO: ${message}`); process.exit(code); }
function sha256(text) { return crypto.createHash('sha256').update(text).digest('hex'); }
function stamp() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${pad(d.getMonth()+1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
}
function replaceOnce(text, search, replacement, label) {
  const first = text.indexOf(search);
  if (first < 0) throw new Error(`Não encontrei o ponto seguro: ${label}.`);
  if (text.indexOf(search, first + search.length) >= 0) throw new Error(`O ponto ${label} apareceu mais de uma vez; abortando para não alterar o local errado.`);
  return text.slice(0, first) + replacement + text.slice(first + search.length);
}
function buildProject() {
  const isWin = process.platform === 'win32';
  const wrapper = path.join(root, isWin ? 'gradlew.bat' : 'gradlew');
  if (!fs.existsSync(wrapper)) { console.warn('[V25] AVISO: gradle wrapper não encontrado.'); return; }
  console.log('\n[V25] Compilando Kotlin...');
  const cmd = isWin ? wrapper : './gradlew';
  const result = spawnSync(cmd, [':app:compileDebugKotlin', '--stacktrace'], { cwd: root, stdio: 'inherit', shell: isWin });
  if (result.error) fail(`Não foi possível iniciar o Gradle: ${result.error.message}`);
  if (result.status !== 0) fail(`A V25 foi aplicada, mas o Gradle retornou código ${result.status}. Os backups foram preservados.`);
  console.log('[V25] BUILD OK.');
}

if (!fs.existsSync(path.join(root, 'settings.gradle.kts'))) fail('Execute este .mjs na raiz do projeto UsinagemMaster.');
for (const [rel, target] of [[linkRel, linkTarget], [overlayRel, overlayTarget], [cloudRel, cloudTarget]]) {
  if (!fs.existsSync(target)) fail(`Não encontrei ${rel}. A V23 Cloud Save precisa estar aplicada.`);
}

const cloudText = fs.readFileSync(cloudTarget, 'utf8');
for (const token of ['suspend fun forceUpload(', 'collection("cloud_saves")', 'CloudSyncAction']) {
  if (!cloudText.includes(token)) fail(`CloudSaveRepository não contém a infraestrutura V23 esperada: ${token}`);
}

let linkOriginal = fs.readFileSync(linkTarget, 'utf8');
let overlayOriginal = fs.readFileSync(overlayTarget, 'utf8');
const alreadyApplied = linkOriginal.includes('// V25_TRANSFER_PROGRESS') && overlayOriginal.includes('// V25_TRANSFER_PROGRESS');
if (alreadyApplied) {
  console.log('\n[V25] Transferência segura já está aplicada.');
  if (runBuild) buildProject();
  process.exit();
}
if (linkOriginal.includes('// V25_TRANSFER_PROGRESS') !== overlayOriginal.includes('// V25_TRANSFER_PROGRESS')) {
  fail('A V25 está parcialmente aplicada em apenas um dos arquivos. Restaure o backup desse arquivo ou envie os dois arquivos para revisão antes de continuar.');
}

let linkUpdated = linkOriginal;
let overlayUpdated = overlayOriginal;
try {
  linkUpdated = replaceOnce(linkUpdated, "    /** Adota o mesmo slot de save ao restaurar a conta em outro aparelho. */\n    fun adoptCloudSave(context: Context, user: FirebaseUser, cloudSaveId: String): AccountLinkState {\n", "    // V25_TRANSFER_PROGRESS\n    /**\n     * Transfere explicitamente o save local para a conta Google atualmente autenticada.\n     * Deve ser chamado SOMENTE após o CloudSaveRepository confirmar o upload do save\n     * atual para o UID de destino. Assim, uma falha de rede nunca troca o dono local\n     * antes de existir um backup recuperável na conta nova.\n     */\n    suspend fun transferCurrentProgress(context: Context, user: FirebaseUser): AccountLinkResult {\n        val before = state(context)\n        require(before.isLinked && before.linkedUid != user.uid) {\n            \"Este progresso não precisa ser transferido para esta conta.\"\n        }\n\n        val now = System.currentTimeMillis()\n        val persisted = prefs(context).edit()\n            .putString(KEY_UID, user.uid)\n            .putString(KEY_EMAIL, user.email)\n            .putString(KEY_NAME, user.displayName)\n            .putLong(KEY_LINKED_AT, now)\n            .commit()\n        check(persisted) { \"Não foi possível atualizar o vínculo local da conta.\" }\n\n        val after = state(context)\n        val cloudOk = runCatching {\n            FirebaseFirestore.getInstance()\n                .collection(\"player_accounts\")\n                .document(user.uid)\n                .set(\n                    mapOf(\n                        \"uid\" to user.uid,\n                        \"email\" to user.email,\n                        \"displayName\" to user.displayName,\n                        \"localSaveId\" to after.localSaveId,\n                        \"provider\" to \"google\",\n                        \"cloudSaveEnabled\" to true,\n                        \"transferredFromUid\" to before.linkedUid,\n                        \"lastLinkedAt\" to FieldValue.serverTimestamp(),\n                        \"lastTransferredAt\" to FieldValue.serverTimestamp(),\n                        \"clientLinkedAtMs\" to now,\n                    ),\n                    SetOptions.merge(),\n                )\n                .await()\n        }.isSuccess\n\n        return AccountLinkResult(after, cloudOk)\n    }\n\n    /** Adota o mesmo slot de save ao restaurar a conta em outro aparelho. */\n    fun adoptCloudSave(context: Context, user: FirebaseUser, cloudSaveId: String): AccountLinkState {\n", 'AccountLinkStore/adoptCloudSave');
  const steps = [{"search": "    var busy by remember { mutableStateOf(false) }\n    var message by remember { mutableStateOf<String?>(null) }\n", "replace": "    var busy by remember { mutableStateOf(false) }\n    var message by remember { mutableStateOf<String?>(null) }\n    var showTransferConfirmation by rememberSaveable { mutableStateOf(false) }\n", "label": "state de confirmação"}, {"search": "    Box(Modifier.fillMaxSize()) {\n        content()\n", "replace": "    // V25_TRANSFER_PROGRESS\n    fun transferProgressToSignedUser(closeAfter: Boolean) {\n        val user = authUser?.takeIf { GoogleAuthBridge.isGoogleUser(it) } ?: return loginAndLink(closeAfter)\n        val before = AccountLinkStore.state(context)\n        if (!before.isLinked || before.linkedUid == user.uid) {\n            message = \"Este progresso já pertence à conta selecionada.\"\n            showTransferConfirmation = false\n            return\n        }\n        if (busy) return\n\n        scope.launch {\n            busy = true\n            message = null\n            runCatching {\n                // Primeiro cria um backup recuperável no UID novo. Somente depois\n                // troca a propriedade local do slot.\n                val uploaded = cloudVm.forceUpload(user, before.localSaveId)\n                val transfer = AccountLinkStore.transferCurrentProgress(context, user)\n                linkState = transfer.state\n\n                if (uploaded.saveId != linkState.localSaveId) {\n                    linkState = AccountLinkStore.adoptCloudSave(context, user, uploaded.saveId)\n                }\n                runCatching { AccountLinkStore.retryCloudRegistry(context, user) }\n                cloudStatus = cloudVm.status()\n                cloudConflict = false\n\n                if (transfer.cloudRegistryUpdated) {\n                    \"Transferência concluída. Este progresso agora pertence a ${user.email ?: \"esta conta Google\"} e o backup v${uploaded.revision} foi salvo na nuvem.\"\n                } else {\n                    \"O save foi transferido e salvo na nuvem, mas o cadastro da conta será atualizado novamente na próxima sincronização.\"\n                }\n            }.onSuccess {\n                message = it\n                showTransferConfirmation = false\n                if (closeAfter) showStartup = false\n            }.onFailure {\n                showTransferConfirmation = false\n                message = it.message ?: \"Não foi possível transferir este progresso. O vínculo anterior foi preservado.\"\n            }\n            busy = false\n        }\n    }\n\n    Box(Modifier.fillMaxSize()) {\n        content()\n", "label": "função de transferência"}, {"search": "            linked = linkState.isLinkedTo(authUser),\n            busy = busy,\n", "replace": "            linked = linkState.isLinkedTo(authUser),\n            linkedToAnotherAccount = linkState.isLinked && !linkState.isLinkedTo(authUser),\n            previousLinkedEmail = linkState.linkedEmail,\n            busy = busy,\n", "label": "estado de conta divergente na tela inicial"}, {"search": "            onLink = { linkSignedUser(true) },\n            onSkip = { showStartup = false },\n", "replace": "            onLink = { linkSignedUser(true) },\n            onTransfer = { showTransferConfirmation = true },\n            onSkip = { showStartup = false },\n", "label": "callback de transferência inicial"}, {"search": "            onLink = { linkSignedUser(false) },\n            onSyncSave = {\n", "replace": "            onLink = { linkSignedUser(false) },\n            onTransfer = { showTransferConfirmation = true },\n            onSyncSave = {\n", "label": "callback de transferência no perfil"}, {"search": "        )\n    }\n}\n\n@Composable\nprivate fun StartupAccountDialog(\n", "replace": "        )\n    }\n\n    if (showTransferConfirmation) {\n        val user = authUser?.takeIf { GoogleAuthBridge.isGoogleUser(it) }\n        if (user != null) {\n            AlertDialog(\n                onDismissRequest = { if (!busy) showTransferConfirmation = false },\n                icon = { Text(\"☁\", style = MaterialTheme.typography.headlineLarge) },\n                title = { Text(\"Transferir este progresso?\", fontWeight = FontWeight.Black) },\n                text = {\n                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {\n                        Text(\n                            \"Este save está vinculado a ${linkState.linkedEmail ?: \"outra conta Google\"}. \" +\n                                \"Você vai transferi-lo para ${user.email ?: user.displayName ?: \"a conta atual\"}.\"\n                        )\n                        Surface(\n                            color = MaterialTheme.colorScheme.errorContainer,\n                            shape = RoundedCornerShape(12.dp),\n                        ) {\n                            Text(\n                                \"O progresso ATUAL deste aparelho será enviado para a conta selecionada. \" +\n                                    \"Se essa conta já possuir um Cloud Save, ele será substituído por este progresso após a confirmação.\",\n                                modifier = Modifier.fillMaxWidth().padding(12.dp),\n                                color = MaterialTheme.colorScheme.onErrorContainer,\n                                fontWeight = FontWeight.Bold,\n                            )\n                        }\n                        Text(\n                            \"A troca de proprietário só acontece depois que o upload do backup termina com sucesso.\",\n                            style = MaterialTheme.typography.bodySmall,\n                        )\n                    }\n                },\n                confirmButton = {\n                    Button(\n                        onClick = { transferProgressToSignedUser(closeAfter = showStartup) },\n                        enabled = !busy,\n                    ) {\n                        if (busy) {\n                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)\n                            Spacer(Modifier.width(8.dp))\n                            Text(\"Transferindo...\")\n                        } else {\n                            Text(\"TRANSFERIR E SALVAR\", fontWeight = FontWeight.Black)\n                        }\n                    }\n                },\n                dismissButton = {\n                    TextButton(onClick = { showTransferConfirmation = false }, enabled = !busy) {\n                        Text(\"Cancelar\")\n                    }\n                },\n            )\n        }\n    }\n}\n\n@Composable\nprivate fun StartupAccountDialog(\n", "label": "diálogo de confirmação"}, {"search": "private fun StartupAccountDialog(\n    user: FirebaseUser?,\n    linked: Boolean,\n    busy: Boolean,\n    message: String?,\n    onGoogle: () -> Unit,\n    onLink: () -> Unit,\n    onSkip: () -> Unit,\n) {\n", "replace": "private fun StartupAccountDialog(\n    user: FirebaseUser?,\n    linked: Boolean,\n    linkedToAnotherAccount: Boolean,\n    previousLinkedEmail: String?,\n    busy: Boolean,\n    message: String?,\n    onGoogle: () -> Unit,\n    onLink: () -> Unit,\n    onTransfer: () -> Unit,\n    onSkip: () -> Unit,\n) {\n", "label": "assinatura da tela inicial"}, {"search": "                Button(\n                    onClick = if (user == null) onGoogle else onLink,\n                    enabled = !busy && !linked,\n                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),\n                ) {\n                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)\n                    else Text(if (user == null) \"G  Continuar com Google\" else if (linked) \"✓ Progresso já vinculado\" else \"Vincular meu progresso atual\")\n                }\n                Spacer(Modifier.height(8.dp))\n                Text(\n                    \"O primeiro vínculo envia seu save atual. Em um aparelho novo, um backup existente é restaurado antes de continuar.\",\n                    style = MaterialTheme.typography.bodySmall,\n                    textAlign = TextAlign.Center,\n                )\n", "replace": "                if (linkedToAnotherAccount && user != null) {\n                    Button(\n                        onClick = onTransfer,\n                        enabled = !busy,\n                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),\n                    ) {\n                        Text(\"Transferir este progresso para esta conta\", fontWeight = FontWeight.Black)\n                    }\n                    Spacer(Modifier.height(8.dp))\n                    Text(\n                        \"Este save está ligado a ${previousLinkedEmail ?: \"outra conta\"}. A transferência cria primeiro o backup na conta atual e só depois muda o proprietário.\",\n                        style = MaterialTheme.typography.bodySmall,\n                        textAlign = TextAlign.Center,\n                    )\n                } else {\n                    Button(\n                        onClick = if (user == null) onGoogle else onLink,\n                        enabled = !busy && !linked,\n                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),\n                    ) {\n                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)\n                        else Text(if (user == null) \"G  Continuar com Google\" else if (linked) \"✓ Progresso já vinculado\" else \"Vincular meu progresso atual\")\n                    }\n                    Spacer(Modifier.height(8.dp))\n                    Text(\n                        \"O primeiro vínculo envia seu save atual. Em um aparelho novo, um backup existente é restaurado antes de continuar.\",\n                        style = MaterialTheme.typography.bodySmall,\n                        textAlign = TextAlign.Center,\n                    )\n                }\n", "label": "botão de transferência na tela inicial"}, {"search": "    onGoogle: () -> Unit,\n    onLink: () -> Unit,\n    onSyncSave: () -> Unit,\n", "replace": "    onGoogle: () -> Unit,\n    onLink: () -> Unit,\n    onTransfer: () -> Unit,\n    onSyncSave: () -> Unit,\n", "label": "assinatura do perfil"}, {"search": "                                } else if (linkState.isLinked) {\n                                    Text(\"⚠ Este save foi vinculado anteriormente a outra conta Google.\", color = MaterialTheme.colorScheme.error)\n                                } else {\n", "replace": "                                } else if (linkState.isLinked) {\n                                    Text(\"⚠ Este save foi vinculado anteriormente a outra conta Google.\", color = MaterialTheme.colorScheme.error)\n                                    Text(\n                                        linkState.linkedEmail?.let { \"Conta anterior: $it\" } ?: \"UID anterior: …${linkState.linkedUid?.takeLast(8).orEmpty()}\",\n                                        style = MaterialTheme.typography.bodySmall,\n                                    )\n                                    Button(\n                                        onClick = onTransfer,\n                                        enabled = !busy,\n                                        modifier = Modifier.fillMaxWidth(),\n                                    ) {\n                                        Text(\"Transferir para esta conta Google\", fontWeight = FontWeight.Black)\n                                    }\n                                } else {\n", "label": "botão de transferência no perfil"}];
  for (const step of steps) overlayUpdated = replaceOnce(overlayUpdated, step.search, step.replace, step.label);
} catch (err) {
  fail(`${err?.message || err} Nenhum arquivo foi alterado.`);
}

const validations = [
  [linkUpdated, 'suspend fun transferCurrentProgress', 'método de transferência'],
  [linkUpdated, 'transferredFromUid', 'auditoria do vínculo anterior'],
  [overlayUpdated, 'cloudVm.forceUpload(user, before.localSaveId)', 'upload antes da troca de vínculo'],
  [overlayUpdated, 'AccountLinkStore.transferCurrentProgress(context, user)', 'troca explícita do vínculo'],
  [overlayUpdated, 'Transferir este progresso para esta conta', 'botão inicial'],
  [overlayUpdated, 'Transferir para esta conta Google', 'botão no perfil'],
  [overlayUpdated, 'TRANSFERIR E SALVAR', 'confirmação forte'],
];
for (const [text, token, label] of validations) if (!text.includes(token)) fail(`Validação interna falhou: ${label}.`);
if (overlayUpdated.indexOf('cloudVm.forceUpload(user, before.localSaveId)') > overlayUpdated.indexOf('AccountLinkStore.transferCurrentProgress(context, user)')) {
  fail('Validação de segurança falhou: a troca do vínculo não pode ocorrer antes do upload.');
}

console.log('\n[V25] Transferência segura de progresso Google');
console.log(`[V25] ${linkRel}`);
console.log(`[V25]   SHA antes : ${sha256(linkOriginal)}`);
console.log(`[V25]   SHA depois: ${sha256(linkUpdated)}`);
console.log(`[V25] ${overlayRel}`);
console.log(`[V25]   SHA antes : ${sha256(overlayOriginal)}`);
console.log(`[V25]   SHA depois: ${sha256(overlayUpdated)}`);
console.log('[V25] Cloud Save V23 detectado: OK');
console.log('[V25] Ordem de segurança upload -> vínculo: OK');

if (checkOnly) {
  console.log('[V25] CHECK OK — pode aplicar sem --force.');
  process.exit();
}

const backupDir = path.join(root, `backup-usinagem-v25-transfer-${stamp()}`);
fs.mkdirSync(path.join(backupDir, path.dirname(linkRel)), { recursive: true });
fs.mkdirSync(path.join(backupDir, path.dirname(overlayRel)), { recursive: true });
fs.copyFileSync(linkTarget, path.join(backupDir, linkRel));
fs.copyFileSync(overlayTarget, path.join(backupDir, overlayRel));

try {
  fs.writeFileSync(linkTarget, linkUpdated, 'utf8');
  fs.writeFileSync(overlayTarget, overlayUpdated, 'utf8');
  if (sha256(fs.readFileSync(linkTarget, 'utf8')) !== sha256(linkUpdated)) throw new Error('SHA pós-gravação falhou em AccountLinkStore.kt');
  if (sha256(fs.readFileSync(overlayTarget, 'utf8')) !== sha256(overlayUpdated)) throw new Error('SHA pós-gravação falhou em AccountRootOverlay.kt');
} catch (err) {
  console.error(`[V25] Falha ao gravar: ${err?.message || err}`);
  console.error('[V25] Restaurando os dois arquivos do backup...');
  fs.copyFileSync(path.join(backupDir, linkRel), linkTarget);
  fs.copyFileSync(path.join(backupDir, overlayRel), overlayTarget);
  fail('Rollback concluído; nenhum arquivo ficou parcialmente atualizado.');
}

console.log(`[V25] Backup: ${backupDir}`);
console.log('[V25] APLICAÇÃO CONCLUÍDA.');
console.log('[V25] Na tela de conta divergente, toque em "Transferir este progresso para esta conta" e confirme "TRANSFERIR E SALVAR".');
console.log('[V25] O upload para cloud_saves ocorre antes da troca do proprietário local.');
if (runBuild) buildProject();
