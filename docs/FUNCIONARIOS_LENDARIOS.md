# Funcionários Lendários

A versão 0.4.0 adiciona 11 funcionários lendários únicos ao sistema de recrutamento.

## Regras

- Lendários aparecem na coleção da tela de Funcionários.
- O botão **Buscar lendário** contrata aleatoriamente um personagem atualmente liberado.
- O valor mostrado na coleção é o primeiro salário/admissão, descontado no momento da contratação.
- O mesmo `legendaryCode` não pode existir duas vezes ao mesmo tempo no Room.
- Se um lendário for demitido, ele pode voltar a aparecer em um recrutamento futuro.
- A progressão de nível da empresa libera novos grupos de lendários.

## Catálogo

| Funcionário | Nível | Especialidade | Traço | Papel |
| --- | ---: | --- | --- | --- |
| Tatu do Banhado | 1 | Torneiro | Casca grossa | bônus em tornos |
| Kendão | 1 | Fresador | Mão pesada | bônus em fresagem |
| Magrão | 1 | Estoque | Logística rápida | +5% produção global |
| Pedrão | 1 | Soldador | Braço de aço | bônus em solda |
| Moskitão | 2 | Furação | Elétrico | bônus em furadeiras |
| Mercião | 2 | Retífica | Acabamento espelho | produção + qualidade em retífica |
| Bodybuilder | 2 | Estoque | Força bruta | +4% produção global |
| Chupa Engole | 3 | Soldador | Rei da solda | bônus forte em solda |
| Nelsinho Treme Treme | 3 | Furação | Treme mas entrega | produtividade com pequena penalidade de qualidade |
| Nikao Narizudo | 4 | Qualidade | Controle total | +6 qualidade global |
| Gumersvaldo | 4 | Programador CNC | Mestre CNC | bônus forte em CNC |

## Fábrica Viva

A cena isométrica reconhece `legendaryCode`:

- lendários recebem identificação dourada e capacete diferenciado;
- Magrão e Moskitão caminham mais rápido;
- Bodybuilder tem escala visual maior;
- Nelsinho Treme Treme possui tremor de animação próprio;
- Nikao circula próximo das máquinas com prancheta;
- Gumersvaldo recebe uniforme visual próprio de CNC.

Os efeitos de produção são calculados no `ProductionEngine`, portanto continuam valendo na progressão offline.
