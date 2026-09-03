# Fábrica Viva 2.0 — motor operacional

Base: `LucasCaeCor/game-usinagem`, commit `c2d4492`.

Esta etapa implementa o motor de rotinas da Fábrica Viva e sua integração com a cena
Compose existente. A economia continua sendo apurada pelo repositório do jogo.

## O que muda no jogo

- Funcionários percorrem corredores para buscar material e ferramentas, preparar
  máquinas, usinar, levar lotes à inspeção e à expedição.
- O trajeto usa busca em largura numa malha com corredores entre as 30 posições
  de máquinas. Movimentar uma máquina reconstrói os caminhos.
- Descanso leva o trabalhador à copa; o fim do expediente o leva à saída.
- Toque em um trabalhador para destacar sua rota e acompanhar tarefa e cansaço.
- Máquinas têm preparação, usinagem, espera de abastecimento, desligamento,
  parada por desgaste e indicação de manutenção recomendada.
- A animação e o som de usinagem acompanham o estado operacional da máquina.
- Pessoas e máquinas são desenhadas em ordem de profundidade. As skins existentes
  continuam sendo desenhadas pelo mesmo sistema de personagens.
- A bronca mantém o caminho do dono nos corredores. A página continua rolando
  com um dedo; a câmera usa dois dedos. Os controles de zoom ficam no canto inferior.
- O motor para quando a tela deixa de ser observada. O som pausa ao minimizar.

## Correções de simulação da empresa

O desconto por cansaço era aplicado por funcionário e novamente sobre o total
no fechamento da produção. Agora é aplicado uma vez. Exemplo: eficiência de 62%
resulta em 62% da vazão, sem uma segunda multiplicação pela média da equipe.

Funcionários em pausa e fábrica fechada deixam de aparecer como máquinas operando
ou consumindo energia de produção. A disponibilidade é reavaliada pelo relógio,
mesmo quando não há nova escrita no banco. Fechamentos de horas já trabalhadas
continuam usando o período de expediente, independentemente do horário atual.

Frações de cansaço são preservadas numa chave adicional do DataStore. Seis
fechamentos de dez minutos acumulam o mesmo cansaço de uma hora contínua, antes
de limites e mudanças de estado. O valor inteiro antigo continua disponível para
a interface; saves antigos inicializam a precisão a partir desse valor.

## Separação das responsabilidades

| Camada | Responsabilidade |
| --- | --- |
| `FactorySimulation` | Estados, tarefas, posições, rotas e passos fixos de 50 ms |
| `MachinesViewModel` | Adapta os dados reais e entrega os quadros da simulação |
| `FactoryLiveSceneStudio` | Desenha máquinas/personagens, seleção e câmera |
| `WorkforceProduction` | Ajusta produção, disponibilidade, energia e cansaço |
| `GameRepositoryImpl` | Mantém o fechamento financeiro e o progresso dos contratos |
| `FatigueAccrual` / `WorkLifeRepository` | Acumula cansaço fracionário e o persiste |

Não há mudança de schema Room, migração destrutiva ou novo banco. Posições e
rotinas são transitórias; os dados econômicos continuam no save existente.
Reabrir a tela ou reiniciar o processo não concede recompensas adicionais.

## Limites desta etapa

Os lotes em movimento representam a produção idle existente. Não são um novo
estoque físico nem contagens exatas de peças. As durações visuais são comprimidas
para leitura no celular. O fechamento continua em ciclos de dez minutos e não
depende de a tela estar aberta ou da quantidade de quadros desenhados.

OEE, fornecedores, compras/consumo persistente de matéria-prima, filas de lotes
persistentes, refugo/retrabalho individual, prazos de manutenção, faltas e eventos
empresariais ainda exigem uma próxima evolução econômica. O alerta de manutenção
usa a conservação existente; não acrescenta quebras aleatórias ou reparos temporizados.
Rotas evitam máquinas, mas ainda não implementam colisão/filas entre pessoas.
Operadores sem máquina aguardam serviço; não foram inventados cargos produtivos
para eles. O cálculo offline de pausas mantém as regras anteriores do projeto.

## Aplicar no Windows

1. Coloque `aplicar-fabrica-viva-2.mjs` na raiz do projeto, ao lado de `gradlew.bat`.
2. Opcionalmente confira compatibilidade sem modificar arquivos:

   ```powershell
   node .\aplicar-fabrica-viva-2.mjs --check
   ```

3. Aplique:

   ```powershell
   node .\aplicar-fabrica-viva-2.mjs
   ```

O instalador compara o conteúdo de todos os arquivos antes de gravar, aceita
diferenças de fim de linha do Windows e cria backup dos originais. Alterações
locais incompatíveis interrompem a instalação antes da substituição. Executar
novamente uma atualização já aplicada não duplica alterações.

Para revisar os arquivos sem instalar, execute fora do projeto:

```powershell
node .\aplicar-fabrica-viva-2.mjs --export .\revisao-fabrica-viva-2
```

O diretório de exportação precisa ainda não existir.

## Validação

Foram acrescentados 20 testes JUnit: acessibilidade das 30 posições, destino
bloqueado, rotina completa, determinismo por intervalo, suspensão/tempo inválido,
descanso, turno, celular, desgaste, remoção, edição de layout, ausência de operador,
velocidade por cansaço, ordem de entrada e regressões de produção/cansaço.

**A compilação Android e esses testes não foram executados com sucesso no ambiente
de entrega.** O wrapper falhou antes de compilar: requer Java 21 ou superior
(class version 65), enquanto o ambiente só disponibiliza Java 17. Também não há
SDK Android instalado. O projeto já configura SDK 37 e um daemon Gradle com JDK 25;
essas versões foram preservadas. O Gradle pode provisionar o daemon configurado
quando houver acesso aos downloads.

Verificações locais: revisão do diff e de chamadas/assinaturas; verificação de
espaços pelo Git; execução do instalador em cópias isoladas para aplicação,
reaplicação, divergência de arquivos, CRLF e exportação. Não foi gerado APK nem
feita validação visual em emulador/celular.

No Android Studio com as dependências do projeto disponíveis:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Depois, confira a fábrica com várias máquinas, envie alguém à copa, troque o
turno, mova uma máquina, teste o toque e a rolagem em celular pequeno e minimize
o aplicativo para conferir a pausa do áudio. As alterações não foram publicadas
no GitHub automaticamente.
