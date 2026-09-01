# v8 — Fábrica Premium + Ganhos 3x

A v8 transforma a tela real do **Galpão / Fábrica Viva** em uma interface de jogo mobile mais próxima de um idle/management premium, sem usar uma imagem estática como cenário.

## Economia

- O lucro passivo de produção é multiplicado por **3x** em todos os fechamentos normais e offline.
- O ciclo continua com 10 minutos para preservar o ritmo do idle.
- A UI mostra `GANHOS / 10 MIN`, selo `3x`, produção do ciclo e contagem regressiva.
- A aceleração consome 1 impulso e simula **+10 minutos instantâneos** sem zerar o relógio do ciclo normal.

## Engajamento e aceleração

### Recompensa diária
- Disponível uma vez por dia local.
- Entrega 2 impulsos de produção.
- Entrega também um bônus em caixa baseado no ganho estimado de um ciclo, com piso mínimo.
- Persistência em DataStore, sem mudança de schema do Room.

### Minigame de produção
- Minigame de precisão com marcador móvel.
- Quanto mais próximo do centro, maior o bônus em caixa.
- Sempre que concluído dentro da disponibilidade, entrega pelo menos 1 impulso; precisão alta entrega 2.
- Cooldown de 15 minutos.

## Galpão Premium

- HUD superior de nível, caixa e impulsos.
- Cards de status para produzindo / espera / pausa.
- Painel de ganhos com leitura rápida.
- Ações grandes para minigame, recompensa diária e aceleração.
- Bottom navigation funcional para Visão geral, Máquinas, Galpão, Equipe e Metas.
- Canvas do galpão aumentado para 650dp.
- Piso isométrico maior e mais preenchido.
- Sprites de máquinas e operadores escalados de forma adaptativa à densidade da tela.
- Área do café ampliada.
- Zoom de 62% a 300%, pinça, pan, duplo toque e controle vertical no canto inferior esquerdo.
- Falas padrão em 8 s; opções 5 / 8 / 12 s.

## Compatibilidade

A v8 não adiciona entidades Room, então o banco continua na versão 4 e saves da v7 são compatíveis.
