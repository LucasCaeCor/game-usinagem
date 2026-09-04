#!/usr/bin/env node
/**
 * Usinagem Master — V24.1
 * Botão "Sincronizar agora" no Perfil — patch cirúrgico e tolerante a alterações.
 *
 * Diferente da V24 original, este script NÃO exige hash exato do
 * AccountRootOverlay.kt. Ele valida a presença da infraestrutura V23 e altera
 * somente o trecho do Perfil, preservando o restante do arquivo.
 *
 * Uso:
 *   node aplicar-usinagem-master-v24-1-sync-perfil-cirurgico.mjs --check
 *   node aplicar-usinagem-master-v24-1-sync-perfil-cirurgico.mjs
 */

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const args = new Set(process.argv.slice(2));
const checkOnly = args.has('--check');
const root = process.cwd();
const rel = 'app/src/main/java/br/com/usinagemmaster/feature/account/AccountRootOverlay.kt';
const target = path.join(root, ...rel.split('/'));

function fail(message, code = 1) {
  console.error(`\n[V24.1] ERRO: ${message}`);
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

if (!fs.existsSync(path.join(root, 'settings.gradle.kts'))) {
  fail('Execute este script na raiz do projeto UsinagemMaster.');
}
if (!fs.existsSync(target)) fail(`Não encontrei ${rel}`);

const original = fs.readFileSync(target, 'utf8');

// Segurança sem depender de hash: só altera um arquivo que realmente contenha
// a infraestrutura de Cloud Save criada na V23.
const required = [
  'AccountCloudSaveViewModel',
  'CloudSaveStatus',
  'CloudSyncAction',
  'ProfileAccountDialog(',
  'onSyncSave',
  'cloudStatus',
  'linkState',
  'AccountIdentityCard(user)',
];
const missing = required.filter((token) => !original.includes(token));
if (missing.length) {
  fail(`O Perfil não contém toda a infraestrutura V23 esperada. Faltando: ${missing.join(', ')}. Nenhum arquivo foi alterado.`);
}

const marker = '// V24_1_PROFILE_SYNC_BUTTON';
if (original.includes(marker) || original.includes('☁ SINCRONIZAR AGORA')) {
  console.log('\n[V24.1] O botão de sincronização já está presente no Perfil.');
  console.log(`[V24.1] SHA-256 atual: ${sha256(original)}`);
  process.exit();
}

const insertion = `

                        // V24_1_PROFILE_SYNC_BUTTON
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("☁ Sincronização Firebase", fontWeight = FontWeight.Black)
                                        Text(
                                            if (linkState.isLinkedTo(user)) {
                                                if (cloudStatus.revision > 0L) {
                                                    "Backup v\${cloudStatus.revision} • \${cloudSyncTime(cloudStatus.syncedAt)}"
                                                } else {
                                                    "Conta vinculada • primeiro backup ainda não criado"
                                                }
                                            } else {
                                                "Vincule este progresso à conta Google para habilitar o backup."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Button(
                                    onClick = onSyncSave,
                                    enabled = !busy && linkState.isLinkedTo(user),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                ) {
                                    if (busy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text("Sincronizando...")
                                    } else {
                                        Text("☁ SINCRONIZAR AGORA", fontWeight = FontWeight.Black)
                                    }
                                }

                                if (!linkState.isLinkedTo(user)) {
                                    Text(
                                        "Primeiro use ‘Vincular progresso atual’. Depois este botão envia o save completo para cloud_saves.",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }

                                message?.let {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(it, Modifier.fillMaxWidth().padding(12.dp))
                                    }
                                }
                            }
                        }
`;

const anchor = '                        AccountIdentityCard(user)';
const pos = original.indexOf(anchor);
if (pos < 0) fail('Não encontrei o ponto seguro AccountIdentityCard(user). Nenhum arquivo foi alterado.');
const afterAnchor = pos + anchor.length;
let updated = original.slice(0, afterAnchor) + insertion + original.slice(afterAnchor);

// Se a V23 ainda possui o botão pequeno antigo dentro do card "Progresso do jogo",
// remove SOMENTE esse botão para evitar duas ações iguais.
const oldButtonPatterns = [
  /\n\s*Button\(onClick = onSyncSave, enabled = !busy, modifier = Modifier\.fillMaxWidth\(\)\) \{\s*\n\s*Text\("☁ Sincronizar save agora"\)\s*\n\s*\}/,
  /\n\s*Button\(onClick = onSyncSave, enabled = !busy, modifier = Modifier\.fillMaxWidth\(\)\) \{ Text\("☁ Sincronizar save agora"\) \}/,
];
for (const pattern of oldButtonPatterns) updated = updated.replace(pattern, '');

// Validações finais mínimas antes de gravar.
const checks = [
  marker,
  'Text("☁ SINCRONIZAR AGORA"',
  'onClick = onSyncSave',
  'enabled = !busy && linkState.isLinkedTo(user)',
];
for (const token of checks) {
  if (!updated.includes(token)) fail(`Falha de validação interna: ${token}`);
}
if (updated === original) fail('Nenhuma alteração seria feita.');

console.log('\n[V24.1] Patch cirúrgico do botão de sincronização');
console.log(`[V24.1] Arquivo: ${rel}`);
console.log(`[V24.1] SHA antes : ${sha256(original)}`);
console.log(`[V24.1] SHA depois: ${sha256(updated)}`);
console.log('[V24.1] Infraestrutura V23 encontrada: OK');
console.log('[V24.1] Ponto de inserção encontrado: OK');

if (checkOnly) {
  console.log('[V24.1] CHECK OK — o patch pode ser aplicado sem sobrescrever o arquivo inteiro.');
  process.exit();
}

const backup = `${target}.backup-v24-1-${stamp()}`;
fs.copyFileSync(target, backup);
try {
  fs.writeFileSync(target, updated, 'utf8');
  const verify = fs.readFileSync(target, 'utf8');
  if (sha256(verify) !== sha256(updated)) throw new Error('SHA-256 após gravação não confere.');
} catch (err) {
  fs.copyFileSync(backup, target);
  fail(`Falha ao gravar; backup restaurado automaticamente. ${err?.message || err}`);
}

console.log(`[V24.1] Backup: ${backup}`);
console.log('[V24.1] APLICAÇÃO CONCLUÍDA.');
console.log('[V24.1] Abra o Perfil e use: ☁ SINCRONIZAR AGORA');
