# v0.7.0 — Zoom, falas e ciclos de 10 minutos

## Câmera da fábrica
- Pinça: zoom 72%–265%.
- Pan: gesto de dois dedos.
- Botões −, + e 100%.
- Duplo toque alterna entre visão normal e aproximação.

## Falas
DataStore guarda `speech_duration_seconds`: 4, 7 ou 10 segundos. O ciclo visual é de 24 segundos e os balões agora quebram texto em até duas linhas.

## Economia
O motor mantém taxas por hora internamente para precisão, mas `ProductionSnapshot` expõe equivalentes por 10 minutos. `GameRepositoryImpl` só consolida blocos completos de 10 minutos, preservando o resto para o ciclo seguinte. O limite offline continua em 8 horas.
