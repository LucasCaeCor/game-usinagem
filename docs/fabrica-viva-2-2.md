# Fábrica Viva 2.2 — depósito e entrega pelo dono

Esta versão é cumulativa: inclui a simulação da 2.0, a organização do galpão da 2.1 e a expedição manual. As regras abaixo substituem as descrições de recebimento automático das versões anteriores.

## Como jogar

1. Operadores buscam material, preparam a máquina, produzem, inspecionam e depositam caixas no estoque de saída, marcado como CARGA na parte inferior do galpão.
2. A cada fechamento de 10 minutos, a produção vira uma carga persistente. O painel mostra peças e valor acumulados. A estimativa do próximo ciclo é separada da carga já liberada.
3. Toque no depósito ou em **Levar carga à entrega**. O dono caminha até o depósito, carrega o carrinho, leva a carga à expedição e descarrega.
4. O dinheiro entra no caixa após a descarga. Um lançamento de produção é registrado nas finanças. O dono retorna ao galpão.
5. Uma carga liberada depois do início da viagem permanece para a próxima. Não existe limite de espera ou penalidade de armazenamento nesta versão.

A visão geral avisa quando há carga pronta e abre o galpão. Mesmo sem máquinas instaladas é possível entregar uma carga pendente. O dono pode entregar com o turno de produção fechado.

Ao suspender a tela, o trajeto pausa. Ao encerrar o processo durante a viagem, a carga ainda não entregue permanece no save e pode ser coletada novamente. Uma entrega já registrada não gera outro pagamento. Não é necessário esperar exatamente o décimo minuto: várias cargas podem acumular para uma única viagem.

## Economia e persistência

- O lucro passivo que antes entrava automaticamente no saldo agora compõe o valor da carga. Os cálculos de produção, energia e multiplicador existentes continuam determinando esse valor líquido.
- Impulsos de produção e produção offline também geram carga. Bônus de contrato, missões e minigame mantêm seus recebimentos próprios; não são somados novamente à entrega.
- A animação de cada caixa representa um lote de trabalho. A contagem exata vem do cálculo econômico do ciclo, não da quantidade de desenhos no Canvas. A disposição das caixas em formação é reconstruída ao reabrir; cargas fechadas são persistidas.
- `production_cargo` guarda ID, valor em centavos, quantidade em milésimos de peça, ciclos, criação e data de entrega. Os IDs do período e a verificação do último fechamento impedem que o mesmo intervalo seja fechado novamente.
- A migração Room 4 → 5 apenas acrescenta a tabela e o índice. Não recria as tabelas de empresa, equipe ou máquinas e não converte dinheiro já recebido em carga.
- A transação de fechamento salva o cursor e a carga juntos. A transação de entrega marca os IDs selecionados, credita o caixa e grava o recibo de forma atômica. A consulta limita os parâmetros por bloco para permitir filas maiores.
- Os dados de rotina/fadiga e XP continuam usando os DataStores existentes, separados do banco Room.

## Organização do código

`FactorySimulation` conduz os operadores até `FactoryFloor.STAGING` e conta depósitos visuais. `FactoryOwnerSimulation` controla a rota e as etapas do dono, aguardando confirmação do repositório antes de retornar. `MachinesViewModel` captura os IDs de uma viagem, bloqueia novos comandos enquanto o dono está ocupado e solicita pagamento somente na chegada. `ProductionCargoDao` é responsável pela transação de entrega.

O Canvas desenha a palete, as caixas e o carrinho a partir desses estados. Nenhuma regra de crédito foi colocada no desenho. A entrega suspende a interação de bronca para evitar dois trajetos simultâneos do dono.

## Aplicação

O pacote contém os arquivos atualizados e um instalador Node.js sem dependências. Ele reconhece a base `c2d4492`, a 2.0, a 2.1 e arquivos que já estejam na 2.2. Não precisa aplicar os pacotes anteriores primeiro.

Coloque `aplicar-fabrica-viva-2-2.mjs` na raiz do projeto, ao lado de `gradlew.bat`. No terminal dessa pasta:

```powershell
node aplicar-fabrica-viva-2-2.mjs --check
node aplicar-fabrica-viva-2-2.mjs
```

O instalador verifica todos os arquivos antes de modificar qualquer um e cria uma pasta `backup-fabrica-viva-2-2-*`. Se houver alterações locais diferentes das versões reconhecidas, ele informa quais arquivos precisam de conciliação. O backup é de código; ele não copia o banco de dados do aparelho.

Para revisar os arquivos sem instalar:

```powershell
node aplicar-fabrica-viva-2-2.mjs --export revisao-fabrica-2-2
```

## Verificações e limites

Executado neste ambiente:

- 7 verificações de SQLite usando o schema 4 do projeto e os comandos SQL da migração/DAO: preservação do save; crédito apenas na entrega; repetição; carga nova durante viagem; rollback quando o recibo falha; fila de 1.101 cargas/overflow; empresa ausente; reabertura do banco. Alguns cenários compartilham a mesma verificação.
- 700 combinações de viewport/projeção no código Java real de geometria, herdado da correção 2.1.
- Aplicação cumulativa do instalador, repetição sem alterações, CRLF, exportação, conflito sem sobrescrita e integridade do RAR.

Adicionados para execução no projeto Android:

- Testes Kotlin do trajeto completo do dono, pagamento condicionado à chegada, acesso com 30 máquinas, pausa/cancelamento e depósitos dos operadores.
- Testes instrumentados usando Room real: entregas concorrentes, fila grande, rollback de recibo e migração 4 → 5 com reabertura. O asset `database-v4.json` é uma cópia do schema 4 já existente.

**APK e testes Kotlin/Android não foram compilados/executados aqui.** O wrapper exige Java 21 (bytecode 65), mas o ambiente fornece Java 17 (bytecode 61), e não há Android SDK instalado. Os testes SQLite não substituem os testes Room instrumentados. A aparência e o trajeto ainda precisam de conferência em aparelho/emulador.

No Android Studio, usando o JDK configurado pelo projeto e SDK 37:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

O segundo comando requer emulador/aparelho. A compilação com KSP deve gerar `app/schemas/br.com.usinagemmaster.data.local.database.GameDatabase/5.json`; inclua o schema gerado ao versionar a mudança.

Conferência manual: aguarde um ciclo ou use um impulso; verifique o saldo antes da viagem; inicie uma entrega; confira o crédito somente após a descarga; tente tocar novamente; feche e reabra antes de descarregar; confirme a continuidade do save e a ausência de um segundo pagamento. Verifique também uma carga nova liberada durante a viagem e a entrega no turno fechado.
