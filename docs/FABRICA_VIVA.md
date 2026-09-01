# Fábrica Viva — visual isométrico animado

A tela **Galpão industrial** possui três modos:

1. **Fábrica viva** — visual isométrico/2.5D animado.
2. **Editar layout** — grade 2D com drag-and-drop para reposicionar máquinas.
3. **Lista** — visão técnica de conservação, operador e produtividade.

## Elementos animados

- Operadores atribuídos às máquinas aparecem fisicamente no galpão.
- Movimento de braços/pernas acompanha o estado de produção.
- Funcionários sem máquina circulam pelos corredores.
- O proprietário circula pela fábrica desde o começo do jogo.
- Uma empilhadeira percorre a área inferior do galpão.
- Torno: giro de spindle/peça.
- Fresadora e centros CNC: movimento de spindle.
- Retífica: disco girando.
- Solda, laser e plasma: partículas/faíscas.
- Luzes industriais: pulso suave.
- Luz de status da máquina: verde operando, vermelha parada.

## Integração com gameplay

A cena é uma representação visual do estado salvo no jogo. Ela não cria uma simulação paralela.

- `MachineEntity.gridX/gridY` define a posição mostrada.
- `EmployeeEntity.assignedMachineId` define o personagem junto da máquina.
- `MachineProduction.isOperating` determina se os efeitos de trabalho são executados.
- O painel de gestão aberto ao tocar na máquina continua usando o `MachinesViewModel` e o `GameRepository` reais.

## Performance

A cena usa apenas um `Canvas` Compose e primitivas vetoriais leves. Não há modelos 3D, texturas grandes ou engine externa nesta etapa. Ao sair da tela, o Composable é descartado e o loop visual deixa de renderizar.

Para uma futura versão 3D real, a arquitetura permite trocar apenas a camada visual do galpão por Filament/SceneView ou uma engine dedicada, mantendo Room, domínio, economia e ViewModels.

## Integração com funcionários lendários (v0.4.0)

A Fábrica Viva lê `EmployeeEntity.legendaryCode` para dar identidade visual aos personagens especiais. Lendários usam identificação dourada; alguns possuem escala, velocidade e animações próprias. Esses efeitos visuais são separados dos bônus econômicos, que ficam no `ProductionEngine` e também valem no cálculo offline.
