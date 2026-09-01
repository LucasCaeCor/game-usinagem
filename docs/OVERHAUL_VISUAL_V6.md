# Overhaul Visual v6

A v6 mantém a arquitetura nativa em Jetpack Compose e aumenta a qualidade visual sem introduzir uma engine 3D externa.

## Filosofia

A animação nunca deve contradizer a simulação:

- máquina só anima produção se `MachineProduction.isOperating == true`;
- operador produtivo permanece junto da máquina;
- operador sem trabalho entra em rotina visual de pausa/café;
- Nikao pode permanecer em inspeção mesmo sem uma máquina operando;
- posição real das máquinas continua vindo do Room.

## Estados visuais de funcionário

`FactoryVisualModels.kt` define:

- WALKING
- WORKING
- INSPECTING
- CARRYING_MATERIAL
- COFFEE_BREAK
- TALKING
- IDLE

Esses estados são visuais e não são persistidos.

## Área de café

A Fábrica Viva possui um cantinho de descanso desenhado no Canvas com:

- bancada;
- cafeteira/garrafa térmica;
- copos;
- zona de segurança;
- personagens em pausa;
- copo individual com vapor;
- animação periódica de beber.

## Máquinas

A função `drawMachineDetailed` despacha detalhes diferentes por família de máquina:

- torno: cabeçote, peça/chuck e cavaco;
- CNC: janela, iluminação interna, spindle e IHM;
- fresadora: coluna, mesa e spindle;
- furadeira: coluna, mesa e movimento vertical;
- retífica: rebolo e faíscas;
- solda: bancada e faíscas;
- laser/plasma: mesa, pórtico e corte;
- EDM: tanque e descarga luminosa.

## Personagens

`drawWorkerDetailed` adiciona botas, uniforme, colete refletivo, mãos, capacete, rosto e acessórios. Os lendários recebem variações de escala, cor e detalhes. A tela de Funcionários usa `EmployeePortrait.kt` para refletir essas diferenças fora do galpão.
