# Fábrica Viva 2.2.1 — compatibilidade com áudio personalizado

O instalador 2.2 interrompia a aplicação quando `FactoryAudioLayer.kt` tinha conteúdo diferente das versões reconhecidas. Essa interrupção preservou os arquivos do projeto.

A 2.2.1 move o áudio utilizado pela Fábrica Viva para `FactorySimulationAudio.kt`. A cena chama essa implementação própria, com os estados das máquinas e controle de ciclo de vida já usados pela 2.2. O instalador e a exportação não contêm `FactoryAudioLayer.kt`; o arquivo existente permanece exatamente como está.

A atualização é cumulativa e inclui a entrega de cargas da 2.2. Não é necessário aplicar primeiro o instalador que apresentou conflito. Os demais arquivos continuam sendo verificados antes de qualquer escrita. Alterações locais neles continuam interrompendo a instalação para conciliação. Não existe opção de sobrescrita forçada.

## Aplicar no Windows

Baixe `aplicar-fabrica-viva-2-2-1.mjs` e coloque na raiz de `UsinagemMaster_Final`, ao lado de `gradlew.bat`. No terminal dessa pasta:

```powershell
node .\aplicar-fabrica-viva-2-2-1.mjs --check
node .\aplicar-fabrica-viva-2-2-1.mjs
```

O instalador cria `backup-fabrica-viva-2-2-1-*` antes de atualizar. A primeira execução com `--check` apenas verifica compatibilidade. Aplique somente a 2.2.1; o instalador antigo ainda apresentará o conflito de áudio.

## Verificação

Foram verificados: instalação sobre a base e versões 2.0, 2.1 e 2.2, preservação byte a byte de um arquivo de áudio personalizado, ausência de escrita com `--check`, backup, repetição, CRLF, exportação, manutenção da proteção nos outros arquivos e integridade do RAR.

Esses testes verificam o instalador. O conteúdo do arquivo personalizado do seu computador não foi recebido nem analisado. A compilação Android continua pendente no Android Studio; o ambiente de preparação tem Java 17, incompatível com o wrapper do projeto, e não possui Android SDK.

As regras de depósito, entrega, persistência e os comandos de teste Android permanecem documentados em `docs/fabrica-viva-2-2.md`. Nesta versão, use os comandos de instalação 2.2.1 acima.
