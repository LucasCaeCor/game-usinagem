# ⚙️ Usinagem Master

> Jogo de gestão industrial para Android em que você começa com uma pequena operação de usinagem e evolui até construir uma fábrica altamente especializada, automatizada e competitiva.

**Usinagem Master** é um jogo de gerenciamento inspirado no universo real da manufatura e da usinagem. O jogador administra máquinas, funcionários, contratos, ferramentas, especializações, pesquisa, expansão do galpão, produtividade, qualidade, finanças e evolução da empresa.

O projeto é desenvolvido nativamente para Android com **Kotlin + Jetpack Compose**.

---

## 🎮 Sobre o jogo

No Usinagem Master, você assume o controle de uma empresa de usinagem e precisa tomar decisões para fazê-la crescer.

Ao longo da progressão, o jogador pode:

- comprar e evoluir máquinas;
- contratar funcionários;
- aceitar contratos industriais;
- aumentar a reputação da empresa;
- expandir o galpão;
- especializar a empresa;
- desbloquear pesquisas e melhorias;
- adquirir ferramentas de produção;
- evoluir o personagem principal;
- conseguir skins, personagens e equipamentos;
- participar da comunidade;
- visitar fábricas de outros jogadores;
- negociar profissionais;
- administrar turnos, cansaço e produtividade;
- construir uma operação cada vez mais eficiente.

A proposta é combinar **gestão**, **progressão**, **coleção**, **simulação industrial** e elementos sociais.

---

## 🏭 Principais sistemas

### Fábrica Viva

A fábrica não é apenas uma lista de máquinas.

O jogo possui uma representação visual do galpão, com:

- máquinas posicionadas no chão de fábrica;
- operadores;
- movimentação dos trabalhadores;
- seleção e gerenciamento de máquinas;
- estados de produção;
- logística;
- ponte rolante;
- área de descanso;
- edição do layout;
- expansão do espaço disponível.

O objetivo é fazer a fábrica parecer um ambiente vivo e não apenas um painel administrativo.

---

### 📋 Contratos

Os contratos são uma das principais fontes de progressão.

Cada contrato pode possuir:

- nível mínimo;
- quantidade de peças;
- qualidade exigida;
- prazo;
- recompensa financeira;
- reputação;
- XP da empresa;
- XP do personagem;
- multa por cancelamento;
- requisitos específicos.

Contratos incompatíveis com o nível atual não devem ocupar a lista principal.

Também existem contratos mais difíceis e contratos especiais, com maiores riscos e recompensas.

#### Estados de contrato

- Disponível
- Ativo
- Concluído
- Falhou
- Cancelado com multa

Contratos concluídos podem ser enviados para o histórico, enquanto contratos com falha podem ser removidos.

---

## 📈 Progressão da empresa

A empresa possui nível e progressão próprios.

A evolução está ligada principalmente a:

- contratos concluídos;
- reputação;
- objetivos;
- expansão da operação;
- pesquisa;
- crescimento produtivo.

O jogador pode acompanhar visualmente o progresso através de uma barra de XP.

### Especializações

A empresa pode seguir especializações como:

- Tornearia
- CNC Torno
- Fresagem
- Centro de Usinagem
- Retífica
- Soldagem
- Produção Geral

A especialização pode alterar bônus, contratos e caminhos de evolução.

---

## 🔬 Pesquisa e árvore de habilidades

O sistema de pesquisa utiliza uma árvore visual de progressão.

Exemplos de ramos:

### Produção
Melhorias voltadas para:

- velocidade;
- capacidade;
- setup;
- produtividade;
- eficiência das máquinas.

### Qualidade
Melhorias voltadas para:

- precisão;
- redução de refugos;
- inspeção;
- acabamento.

### Gestão
Melhorias voltadas para:

- funcionários;
- custos;
- reputação;
- administração;
- contratos.

Também existe uma árvore separada para o personagem principal.

---

## 👷 Personagem principal

O jogador possui um personagem principal com progressão própria.

Ele pode ganhar XP ao:

- concluir contratos;
- participar de trabalhos mais difíceis;
- realizar trabalhos de alta qualidade;
- desbloquear skills pessoais;
- trabalhar temporariamente em outra empresa.

O personagem pode oferecer vantagens para a própria empresa dependendo das habilidades desbloqueadas.

---

## 👥 Funcionários e exaustão

Os trabalhadores fazem parte da produtividade da fábrica.

O sistema considera:

- operador responsável;
- produtividade;
- exaustão;
- descanso;
- eficiência individual.

### Turno padrão

A empresa pode trabalhar em um turno normal, por exemplo:

```text
07:00 → 19:00
```

Fora do expediente:

- a produção é pausada;
- os funcionários descansam;
- a exaustão diminui;
- o tempo útil dos contratos pode ser pausado.

### Operação 24 horas

Também é possível manter a empresa funcionando continuamente.

Nesse modo:

- os funcionários acumulam exaustão;
- a produtividade diminui conforme o cansaço aumenta;
- trabalhadores precisam descansar;
- o gerenciamento da equipe se torna mais importante.

---

## ☕ Copa / área de descanso

A fábrica possui uma área de descanso para os trabalhadores.

A Copa pode conter visualmente:

- sofás;
- mesas;
- bancos;
- área de café.

Funcionários cansados podem ser enviados para descansar antes de voltar ao trabalho.

O sistema pode permitir descanso manual ou automático.

---

## 🧰 Ferramentas

Ferramentas podem ser usadas para melhorar a eficiência dos contratos.

Exemplos:

- brocas básicas;
- ferramentas soldadas;
- fresas de aço rápido;
- metal duro;
- ferramentas de alto avanço;
- CBN;
- PCD.

Cada ferramenta pode oferecer vantagens diferentes, como:

- maior velocidade;
- melhor qualidade;
- menor tempo de produção;
- maior eficiência.

Algumas ferramentas são mais raras e valiosas que outras.

---

## 🎰 Roleta Industrial

A Roleta Industrial funciona como um sistema de recompensa e coleção.

Entre os tipos de prêmio podem existir:

- personagens;
- ferramentas;
- skins;
- máquinas especiais.

### Regras importantes

- fichas são usadas para girar;
- fichas não devem ser prêmio da própria roleta;
- personagens já adquiridos não devem ser sorteados novamente;
- personagens premium ficam fora da roleta;
- sistemas de pity podem aumentar a chance de prêmios raros depois de várias tentativas.

---

## 💎 Personagens Premium

Personagens premium são especialistas adquiridos diretamente na loja.

Eles possuem:

- preços elevados;
- bônus permanentes;
- vantagens específicas;
- raridade maior.

Exemplos de benefícios:

- bônus de torneamento;
- ganho de qualidade;
- redução de setup;
- aumento de produtividade;
- bônus em CNC.

Personagens premium não precisam fazer parte do mercado de aluguel entre jogadores.

---

## 🏭 Máquinas

Máquinas são o núcleo da produção.

O sistema suporta:

- compra;
- evolução;
- conservação;
- operador;
- produção;
- posicionamento no layout;
- máquinas especiais;
- máquinas premium.

Máquinas premium podem possuir multiplicadores muito superiores às máquinas comuns, mas também custam muito mais.

Máquinas obtidas através de recompensas precisam ser registradas no inventário/galpão real do jogador.

---

## 🏗️ Expansão do galpão

O jogador pode aumentar o espaço disponível da fábrica.

Antes da compra, a interface pode mostrar:

- nível atual;
- próximo nível;
- área atual;
- nova área;
- preço;
- saldo atual;
- saldo após a expansão;
- valor que ainda falta caso não haja dinheiro suficiente.

---

## 🏆 Metas

O jogo possui objetivos de curto, médio e longo prazo.

Exemplos:

- atingir determinada quantidade de máquinas;
- contratar funcionários;
- alcançar níveis altos de reputação;
- expandir o galpão;
- chegar a níveis avançados da empresa.

Metas de late game oferecem recompensas maiores e podem incluir fichas de roleta.

---

## 🌐 Comunidade

A camada social utiliza Firebase.

Jogadores com uma conta online vinculada podem compartilhar informações públicas da fábrica.

### Visitar outras fábricas

A comunidade pode permitir:

1. acessar a área **Comunidade**;
2. visualizar jogadores/fábricas;
3. tocar no perfil;
4. entrar na fábrica em modo visitante.

A visita é apenas para visualização.

O visitante não pode:

- mover máquinas;
- vender itens;
- alterar funcionários;
- iniciar produção;
- modificar a fábrica visitada.

---

## 🤝 Mercado de profissionais

O personagem principal de um jogador pode ser disponibilizado para outras empresas.

Um profissional contratado temporariamente pode oferecer benefícios baseados em:

- nível;
- skills;
- especialização;
- experiência.

Exemplo de duração:

```text
48 horas
```

Personagens premium comprados na loja são um sistema separado e não precisam participar desse mercado.

---

## 🔐 Conta Google e Firebase

O jogo pode funcionar localmente sem Firebase.

Para recursos online, o projeto utiliza:

- Firebase Authentication;
- Google Sign-In;
- Cloud Firestore.

A conta Google fornece uma identidade única para recursos sociais, como comunidade e mercado.

O progresso local deve ser vinculado à conta sem apagar a empresa existente.

---

# 🛠️ Tecnologias

O projeto utiliza atualmente:

- **Kotlin**
- **Jetpack Compose**
- **Material 3**
- **Navigation Compose**
- **Room**
- **DataStore Preferences**
- **Hilt**
- **KSP**
- **WorkManager**
- **Kotlin Coroutines / Flow**
- **Firebase Authentication**
- **Cloud Firestore**

### Configuração Android

```text
Application ID: br.com.usinagemmaster
Min SDK: 24
Target SDK: 37
Compile SDK: 37
Java: 17
```

---

# 📁 Estrutura do projeto

```text
app/
└── src/main/java/br/com/usinagemmaster/
    ├── app/
    │   └── navigation/
    │
    ├── core/
    │   └── componentes e infraestrutura compartilhada
    │
    ├── data/
    │   ├── local/
    │   │   ├── Room
    │   │   ├── DAO
    │   │   └── entidades
    │   └── repository/
    │
    ├── di/
    │   └── módulos Hilt
    │
    ├── domain/
    │   └── modelos e regras de negócio
    │
    ├── feature/
    │   ├── dashboard/
    │   ├── machines/
    │   ├── contracts/
    │   ├── employees/
    │   ├── store/
    │   ├── social/
    │   └── ...
    │
    ├── worker/
    │   └── tarefas em background
    │
    ├── MainActivity.kt
    └── UsinagemMasterApplication.kt
```

A organização segue uma separação por camadas e funcionalidades para facilitar a evolução do projeto.

---

# 🚀 Como executar

## Pré-requisitos

Recomendado:

- Android Studio atualizado;
- Android SDK instalado;
- JDK 21 para executar o Gradle Wrapper do projeto;
- dispositivo Android ou emulador;
- Node.js apenas caso seja necessário executar algum instalador/patch `.mjs`.

> O código Android continua configurado com compatibilidade Java 17. O JDK 21 pode ser necessário para executar o Gradle utilizado pelo projeto.

---

## 1. Clone o projeto

```bash
git clone https://github.com/LucasCaeCor/game-usinagem.git
cd game-usinagem
```

---

## 2. Configure o Java no Windows

Exemplo utilizando Microsoft OpenJDK 21:

```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

Confirme:

```powershell
java -version
```

---

## 3. Compile

No Windows:

```powershell
.\gradlew.bat clean assembleDebug
```

Em Linux/macOS:

```bash
./gradlew clean assembleDebug
```

---

## 4. Execute

Abra o projeto no Android Studio e rode o módulo:

```text
app
```

em um dispositivo ou emulador Android.

---

# 🔥 Configuração do Firebase

Firebase é necessário apenas para funcionalidades online.

O jogo local pode continuar compilando sem `google-services.json`.

## 1. Crie/configure um projeto Firebase

No Firebase Console, registre um aplicativo Android com:

```text
br.com.usinagemmaster
```

---

## 2. Adicione SHA-1 e SHA-256

No terminal:

```powershell
.\gradlew.bat signingReport
```

Cadastre os fingerprints no Firebase.

---

## 3. Habilite Google Sign-In

No Firebase:

```text
Authentication
→ Sign-in method
→ Google
→ Ativar
```

---

## 4. Baixe `google-services.json`

Coloque em:

```text
app/google-services.json
```

O Gradle do projeto aplica o plugin Google Services somente quando esse arquivo existe.

---

## 5. Firestore

Recursos sociais podem utilizar coleções como:

```text
player_accounts
public_factories
character_offers
character_rentals
```

As regras do Firestore devem ser publicadas no servidor Firebase para começarem a valer.

Alterar apenas o arquivo local de regras não publica as alterações.

---

# 💾 Persistência

O jogo utiliza diferentes formas de persistência.

### Room

Usado para dados principais do jogo, como:

- empresa;
- máquinas;
- funcionários;
- contratos;
- finanças;
- produção.

### DataStore

Usado para preferências e sistemas auxiliares, como configurações e estados que não precisam alterar o schema principal do banco.

### Firestore

Usado para dados online/social, sem substituir o save local principal.

---

# 🔒 Segurança do save

Ao evoluir o projeto, alterações no Room devem ser tratadas com cuidado.

Sempre que possível:

- preserve bancos existentes;
- use migrations;
- evite `fallbackToDestructiveMigration` para saves reais;
- mantenha transações financeiras idempotentes;
- não vincule login Google à criação automática de uma empresa nova;
- faça backup antes de alterações estruturais.

---

# 🧪 Build de debug

```powershell
.\gradlew.bat clean assembleDebug
```

APK normalmente gerado em:

```text
app/build/outputs/apk/debug/
```

---

# 📦 Release

Antes de publicar na Play Store:

- configurar signing de release;
- cadastrar SHA-1/SHA-256 de produção no Firebase;
- configurar Play App Signing;
- testar migrations do Room;
- revisar regras do Firestore;
- ativar R8/minify quando apropriado;
- testar diferentes tamanhos de tela;
- revisar performance da Fábrica Viva;
- testar modo offline/online.

---

# 🗺️ Roadmap

Algumas direções planejadas para o projeto:

- [ ] balanceamento completo da economia;
- [ ] mais máquinas e especializações;
- [ ] novos contratos especiais;
- [ ] eventos industriais;
- [ ] animações e sons;
- [ ] mais personagens e skins;
- [ ] rankings;
- [ ] visitas sociais mais completas;
- [ ] sistema de turnos avançado;
- [ ] manutenção e quebra de máquinas;
- [ ] fornecedores;
- [ ] estoque de matéria-prima;
- [ ] desafios semanais;
- [ ] conquistas;
- [ ] preparação para Google Play.

---

# 🎯 Visão do projeto

O objetivo do **Usinagem Master** é evoluir de um simulador simples de produção para um jogo de gestão industrial completo, no qual cada decisão influencia a fábrica:

```text
Comprar máquina
      ↓
Contratar operador
      ↓
Aceitar contratos
      ↓
Produzir
      ↓
Ganhar dinheiro + XP + reputação
      ↓
Pesquisar melhorias
      ↓
Expandir
      ↓
Especializar a empresa
      ↓
Construir uma fábrica cada vez maior
```

A longo prazo, a proposta é unir:

**gestão industrial + progressão + coleção + estratégia + comunidade.**

---

# 👨‍💻 Desenvolvimento

Projeto:

**Usinagem Master**

Repositório:

https://github.com/LucasCaeCor/game-usinagem

Desenvolvido para Android.

---

## ⚠️ Status

O projeto está **em desenvolvimento ativo**.

Sistemas, economia, interface, balanceamento e recursos online ainda podem mudar durante o desenvolvimento.

---

<p align="center">
  <strong>⚙️ USINAGEM MASTER</strong><br>
  Construa. Produza. Evolua.
</p>
