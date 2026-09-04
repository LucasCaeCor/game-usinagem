#!/usr/bin/env node
/**
 * Usinagem Master — V26
 * Botão "Apagar dados locais deste aparelho" para testar restauração Cloud Save.
 *
 * Patch cirúrgico sobre V25 (compatível com V25 + V24.1).
 * Não altera Firestore Rules e NÃO apaga dados remotos.
 *
 * Uso:
 *   node aplicar-usinagem-master-v26-apagar-dados-locais.mjs --check
 *   node aplicar-usinagem-master-v26-apagar-dados-locais.mjs
 *   node aplicar-usinagem-master-v26-apagar-dados-locais.mjs --build
 */

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';

const args = new Set(process.argv.slice(2));
const checkOnly = args.has('--check');
const runBuild = args.has('--build');
const root = process.cwd();
const rel = 'app/src/main/java/br/com/usinagemmaster/feature/account/AccountRootOverlay.kt';
const target = path.join(root, ...rel.split('/'));
const marker = '// V26_LOCAL_RESET';

function fail(message, code = 1) {
  console.error(`\n[V26] ERRO: ${message}`);
  process.exit(code);
}
function sha256(text) {
  return crypto.createHash('sha256').update(text).digest('hex');
}
function stamp() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${pad(d.getMonth()+1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
}
function replaceOnce(text, from, to, label) {
  const first = text.indexOf(from);
  if (first < 0) fail(`Não encontrei o ponto seguro: ${label}. Nenhum arquivo foi alterado.`);
  if (text.indexOf(from, first + from.length) >= 0) fail(`Ponto ambíguo (${label}) apareceu mais de uma vez. Nenhum arquivo foi alterado.`);
  return text.slice(0, first) + to + text.slice(first + from.length);
}
function buildProject() {
  const isWin = process.platform === 'win32';
  const wrapper = path.join(root, isWin ? 'gradlew.bat' : 'gradlew');
  if (!fs.existsSync(wrapper)) {
    console.warn('[V26] AVISO: Gradle wrapper não encontrado; patch aplicado, build não executado.');
    return;
  }
  console.log('\n[V26] Compilando Kotlin do app...');
  const cmd = isWin ? wrapper : './gradlew';
  const result = spawnSync(cmd, [':app:compileDebugKotlin', '--stacktrace'], {
    cwd: root,
    stdio: 'inherit',
    shell: isWin,
  });
  if (result.error) fail(`Não foi possível iniciar o Gradle: ${result.error.message}`);
  if (result.status !== 0) fail(`O patch foi aplicado, mas o Gradle retornou código ${result.status}.`);
  console.log('[V26] BUILD OK: :app:compileDebugKotlin concluído.');
}

if (!fs.existsSync(path.join(root, 'settings.gradle.kts'))) {
  fail('Execute este script na raiz do projeto UsinagemMaster.');
}
if (!fs.existsSync(target)) fail(`Não encontrei ${rel}`);

const original = fs.readFileSync(target, 'utf8');
const required = [
  'AccountCloudSaveViewModel',
  'CloudSyncAction',
  'transferProgressToSignedUser',
  'ProfileAccountDialog(',
  'onForceRestore',
  'onSignOut',
  'AccountLinkStore.state(context)',
];
const missing = required.filter((token) => !original.includes(token));
if (missing.length) {
  fail(`A V25 esperada não foi encontrada. Faltando: ${missing.join(', ')}.`);
}

if (original.includes(marker) || original.includes('APAGAR DADOS LOCAIS DESTE APARELHO')) {
  console.log('\n[V26] O recurso de apagar dados locais já está presente.');
  console.log(`[V26] SHA-256 atual: ${sha256(original)}`);
  if (runBuild) buildProject();
  process.exit();
}

let updated = original;

// 1) Estado do diálogo.
updated = replaceOnce(
  updated,
  '    var showTransferConfirmation by rememberSaveable { mutableStateOf(false) }\n',
  '    var showTransferConfirmation by rememberSaveable { mutableStateOf(false) }\n' +
  '    var showLocalResetConfirmation by rememberSaveable { mutableStateOf(false) }\n',
  'estado showTransferConfirmation',
);

// 2) Função que sincroniza e só então pede ao Android para limpar TODOS os dados locais do app.
const rootBoxAnchor = '\n    Box(Modifier.fillMaxSize()) {\n';
const rootBoxPos = updated.indexOf(rootBoxAnchor);
if (rootBoxPos < 0) fail('Não encontrei o início seguro da UI do AccountRootOverlay.');
const wipeFunction = `

    // V26_LOCAL_RESET
    fun wipeLocalDataAfterCloudBackup() {
        val user = authUser?.takeIf { linkState.isLinkedTo(it) && GoogleAuthBridge.isGoogleUser(it) }
        if (user == null) {
            message = "Vincule este progresso à conta Google antes de apagar os dados locais."
            showLocalResetConfirmation = false
            return
        }
        if (busy) return

        scope.launch {
            busy = true
            message = null
            runCatching {
                // Antes de apagar qualquer byte local, confirma uma revisão recuperável.
                val sync = syncCloud(user, visibleMessage = false)
                require(sync.action != CloudSyncAction.CONFLICT) {
                    "Existe conflito entre este aparelho e a nuvem. Resolva o conflito antes de apagar os dados locais."
                }
                cloudStatus = cloudVm.status()
                require(cloudStatus.revision > 0L) {
                    "O backup ainda não foi confirmado na nuvem. Sincronize novamente antes de apagar os dados locais."
                }

                // API oficial do Android para limpar os dados do próprio aplicativo.
                // Remove Room, DataStore, SharedPreferences, FirebaseAuth local e localSaveId,
                // mas NÃO toca no Firestore/Firebase Authentication remoto.
                val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                    ?: error("Não foi possível acessar o gerenciador de dados do Android.")
                val started = activityManager.clearApplicationUserData()
                check(started) { "O Android recusou a limpeza dos dados locais do aplicativo." }
            }.onFailure {
                showLocalResetConfirmation = false
                message = it.message ?: "Não foi possível apagar os dados locais. Nenhum dado da nuvem foi removido."
                busy = false
            }
            // Em caso de sucesso, o Android encerra o processo durante a limpeza.
        }
    }
`;
updated = updated.slice(0, rootBoxPos) + wipeFunction + updated.slice(rootBoxPos);

// 3) Callback na chamada do ProfileAccountDialog.
const callAnchor = `            onSignOut = {
                val user = authUser?.takeIf { GoogleAuthBridge.isGoogleUser(it) }
`;
updated = replaceOnce(
  updated,
  callAnchor,
  `            onClearLocalData = { showLocalResetConfirmation = true },
${callAnchor}`,
  'callback onSignOut na chamada do Perfil',
);

// 4) Parâmetro novo do ProfileAccountDialog.
updated = replaceOnce(
  updated,
  '    onForceRestore: () -> Unit,\n    onSignOut: () -> Unit,\n) {\n',
  '    onForceRestore: () -> Unit,\n    onClearLocalData: () -> Unit,\n    onSignOut: () -> Unit,\n) {\n',
  'assinatura ProfileAccountDialog',
);

// 5) Card visível no Perfil, antes de "Desconectar Google".
const signOutButton = `                        OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Desconectar Google deste aparelho")
                        }
`;
const localResetCard = `                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                            ),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🧪 Testar recuperação da nuvem", fontWeight = FontWeight.Black)
                                Text(
                                    "Simula um celular novo: faz uma última sincronização e apaga somente os dados locais deste aplicativo.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    if (cloudStatus.revision > 0L) "Backup local conhecido: v\${cloudStatus.revision}."
                                    else "O jogo criará/confirmará o backup antes de permitir a limpeza.",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                OutlinedButton(
                                    onClick = onClearLocalData,
                                    enabled = !busy && linkState.isLinkedTo(user),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                ) {
                                    Text("APAGAR DADOS LOCAIS DESTE APARELHO", fontWeight = FontWeight.Black)
                                }
                            }
                        }

${signOutButton}`;
updated = replaceOnce(updated, signOutButton, localResetCard, 'botão Desconectar Google no Perfil');

// 6) Confirmação forte. Inserida antes do fim de AccountRootOverlay.
const startupBoundary = '\n}\n\n@Composable\nprivate fun StartupAccountDialog';
const boundaryPos = updated.indexOf(startupBoundary);
if (boundaryPos < 0) fail('Não encontrei o final seguro de AccountRootOverlay.');
const confirmDialog = `

    if (showLocalResetConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!busy) showLocalResetConfirmation = false },
            icon = { Text("🧹", style = MaterialTheme.typography.headlineLarge) },
            title = { Text("Apagar dados locais deste aparelho?", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Antes de apagar, o jogo fará uma última sincronização com o Firebase. " +
                            "Se o backup falhar ou existir conflito, a limpeza será cancelada."
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            "Isto apaga deste aparelho: banco local da fábrica, preferências, saveId, vínculo local e sessão Google/Firebase. " +
                                "NÃO apaga cloud_saves, player_accounts nem o usuário do Firebase Authentication.",
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "Depois, abra o jogo novamente e entre com a mesma conta Google para testar a restauração como se fosse um celular novo.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { wipeLocalDataAfterCloudBackup() },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Protegendo backup...")
                    } else {
                        Text("SINCRONIZAR E APAGAR LOCAL", fontWeight = FontWeight.Black)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocalResetConfirmation = false }, enabled = !busy) {
                    Text("Cancelar")
                }
            },
        )
    }
`;
updated = updated.slice(0, boundaryPos) + confirmDialog + updated.slice(boundaryPos);

const checks = [
  marker,
  'showLocalResetConfirmation',
  'clearApplicationUserData()',
  'onClearLocalData',
  'APAGAR DADOS LOCAIS DESTE APARELHO',
  'SINCRONIZAR E APAGAR LOCAL',
  'NÃO apaga cloud_saves',
];
for (const token of checks) {
  if (!updated.includes(token)) fail(`Falha de validação interna: ${token}`);
}
if (updated === original) fail('Nenhuma alteração seria feita.');

console.log('\n[V26] Apagar dados locais / teste de restauração');
console.log(`[V26] Arquivo: ${rel}`);
console.log(`[V26] SHA antes : ${sha256(original)}`);
console.log(`[V26] SHA depois: ${sha256(updated)}`);
console.log('[V26] V25 detectada: OK');
console.log('[V26] A limpeza só ocorre após uma sincronização válida e sem conflito.');
console.log('[V26] Firestore e usuário remoto NÃO são apagados.');

if (checkOnly) {
  console.log('[V26] CHECK OK — pode aplicar sem --force.');
  process.exit();
}

const backup = `${target}.backup-v26-${stamp()}`;
fs.copyFileSync(target, backup);
try {
  fs.writeFileSync(target, updated, 'utf8');
  const verify = fs.readFileSync(target, 'utf8');
  if (sha256(verify) !== sha256(updated)) throw new Error('SHA-256 após gravação não confere.');
} catch (err) {
  fs.copyFileSync(backup, target);
  fail(`Falha ao gravar; backup restaurado automaticamente. ${err?.message || err}`);
}

console.log(`[V26] Backup: ${backup}`);
console.log('[V26] APLICAÇÃO CONCLUÍDA.');
console.log('[V26] Perfil → 🧪 Testar recuperação da nuvem → APAGAR DADOS LOCAIS DESTE APARELHO.');
console.log('[V26] Após a limpeza, abra o app e entre com a mesma conta Google para restaurar o Cloud Save.');
if (runBuild) buildProject();
