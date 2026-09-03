# Fábrica Viva 2.1 — correção do galpão no celular

Corrige a sobreposição de máquinas, personagens e textos visível na captura da
versão 2.0. A projeção anterior aproximava os centros das máquinas enquanto
mantinha os desenhos grandes; o conteúdo ocupava apenas a parte superior da cena.

## Alterações

- Vista em cinco colunas e três a seis fileiras, conforme a última posição ocupada.
  As coordenadas salvas das máquinas continuam iguais.
- Escala dos desenhos limitada pela largura e altura de cada posição. Não há mais
  uma escala mínima que faça as máquinas ultrapassarem as posições vizinhas.
- Nomes e tarefas ficam no painel do item selecionado. A cena mostra luzes de
  estado e barras compactas de preparação/usinagem.
- Cabeçalho fora da área de desenho e recorte da câmera nos limites do galpão.
- Toques, zoom, rotas, estações e desenhos compartilham a mesma projeção.
- Funcionários começam perto da máquina atribuída e possuem posições distintas
  na área de descanso, reduzindo a concentração inicial numa única entrada.
- Falas automáticas são mostradas somente para o personagem selecionado.

Esta correção não introduz novas regras de dinheiro, contratos ou estoque. O pacote
é cumulativo: aceita a base `c2d4492` e os arquivos da atualização 2.0. Arquivos já
iguais são ignorados; outras alterações locais interrompem a aplicação antes da
substituição. O instalador salva backup dos arquivos alterados.

## Aplicar

Extraia o RAR e copie `aplicar-fabrica-viva-2-1.mjs` para a raiz do projeto, ao
lado de `gradlew.bat`. Execute nessa pasta:

```powershell
node .\aplicar-fabrica-viva-2-1.mjs
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Para conferir compatibilidade sem aplicar, acrescente `--check` ao comando Node.

## Verificação realizada

A classe de projeção usada pelo aplicativo foi compilada isoladamente com Java 17.
O verificador `tools/FactoryGeometryCheck.java` passou em 700 combinações de largura,
altura, densidade e fileiras. Foram conferidos os limites dos corpos das máquinas,
a ausência de sobreposição desses limites, a projeção dos corredores e a seleção
de máquinas após zoom e deslocamento da câmera. Partículas e pessoas móveis não
são tratadas como colisões rígidas por esse verificador.

Também foram verificadas a aplicação sobre a versão original e sobre a 2.0,
reaplicação, backup, proteção contra alterações locais e integridade do RAR.

O build Android completo, a renderização no emulador/celular e os testes Kotlin
ainda precisam ser executados no ambiente Android. O ambiente de entrega tem
Java 17, e o wrapper do projeto exige versão superior; não foi gerado APK.

Para repetir somente a verificação de geometria com JDK instalado:

```powershell
javac -d build\geometry-check app\src\main\java\br\com\usinagemmaster\feature\machines\FactorySceneGeometry.java tools\FactoryGeometryCheck.java
java -cp build\geometry-check FactoryGeometryCheck
```

Os limites econômicos da etapa 2.0 permanecem documentados em `fabrica-viva-2.md`.
