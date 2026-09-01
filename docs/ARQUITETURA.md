# Arquitetura — Usinagem Master

## Fluxo de dados
Compose Screen → ViewModel → GameRepository → DAOs/DataStore → Room.

A UI apenas observa `StateFlow` e envia intenções. Regras de economia e persistência ficam fora dos composables.

## Motor idle
O jogo salva `lastSimulationAt`. Na próxima inicialização calcula o tempo decorrido, limita a janela offline a 8 horas e aplica a produção estimada. Isso evita manter timers/processos em background e respeita o modelo moderno do Android.

## Catálogo x estado salvo
Características fixas de máquinas (preço base, produção/hora, qualidade, consumo e espaço) ficam em `MachineCatalog`. O Room guarda somente o estado adquirido pelo jogador (nível, conservação, posição e horas). Assim é possível rebalancear o catálogo sem migrar cada máquina do save.

## Dinheiro
Todos os valores monetários persistidos usam `Long` em centavos. Nunca `Float`/`Double` para caixa, salário ou preço.

## Evolução planejada
Quando o projeto crescer, o `GameRepository` pode ser dividido em `CompanyRepository`, `MachineRepository`, `EmployeeRepository`, `ContractRepository` e `FinanceRepository` sem mudar as telas, pois as camadas já estão separadas.
