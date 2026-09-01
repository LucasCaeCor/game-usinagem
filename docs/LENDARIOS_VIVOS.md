# Lendários Vivos — v0.5.0

## Objetivo
Dar personalidade, progressão e feedback audiovisual aos funcionários lendários sem desconectar a animação da simulação real.

## Falas contextuais
Cada lendário possui falas de trabalho e de ociosidade. A Fábrica Viva mostra balões em ciclos lentos para evitar poluição visual. As falas podem ser desligadas em Configurações.

## Áudio
Os três WAVs em `app/src/main/res/raw` foram sintetizados no próprio projeto:

- `factory_ambient.wav`: ruído ambiente industrial em loop;
- `machine_tick.wav`: impacto mecânico curto;
- `weld_spark.wav`: estalo de solda/corte.

`FactoryAudioLayer.kt` usa `MediaPlayer` para ambiente e `SoundPool` para efeitos curtos. A opção "Som do jogo" do DataStore controla tudo.

## Missões pessoais
Cada lendário possui exatamente uma missão. A missão é criada quando o funcionário é contratado e permanece pausada caso ele seja demitido, retomando se voltar à empresa.

| Lendário | Missão | Regra | Prêmio |
|---|---|---|---:|
| Tatu do Banhado | Casca grossa no torno | 120 min em torno | R$ 7.000 |
| Kendão | Fresa sem dó | 120 min em fresagem | R$ 8.000 |
| Chupa Engole | Faísca até o fim | 100 min de solda | R$ 9.000 |
| Moskitão | Furação relâmpago | 100 min em furação | R$ 7.800 |
| Nikao Narizudo | Nada passa torto | 90 min com qualidade >= 75% | R$ 11.500 |
| Gumersvaldo | Programa perfeito | 150 min em CNC | R$ 16.000 |
| Magrão | Material não pode parar | 90 min com 2+ máquinas operando | R$ 8.500 |
| Pedrão | Braço de aço | 130 min em solda/caldeiraria | R$ 9.500 |
| Nelsinho Treme Treme | Treme mas entrega | 80 min em furação | R$ 7.000 |
| Mercião | Espelho no aço | 110 min em retífica | R$ 10.500 |
| Bodybuilder | Logística pesada | 120 min com 3+ máquinas operando | R$ 11.000 |

Valores no banco são armazenados em centavos.

## Persistência
Room foi atualizado da versão 3 para 4 com a tabela `legendary_missions` e índice único em `legendaryCode`. A migração preserva saves anteriores.
