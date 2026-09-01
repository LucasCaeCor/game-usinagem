# FIX FINAL 1.0 — Fábrica Viva + Apoiar Firebase

Este pacote é incremental. Ele NÃO recria o projeto e NÃO altera Room/save/economia.

## O que corrige

### Fábrica Viva / UI-UX
- Mantém Minigame, Bônus, +10 min e Copa sempre visíveis.
- Header mais compacto, com estado AO VIVO.
- Fábrica ocupa melhor o espaço da tela.
- Operadores têm micro-rotina mais perceptível diante da máquina (aproximação, painel, inspeção e retorno).
- Máquinas exibem estado de produção na própria célula, sem placas grandes atravessando o cenário.
- Toque em máquina passa a selecionar/destacar primeiro.
- Cartão rápido mostra máquina, conservação, operador e botão Gerenciar.
- Célula selecionada recebe contorno amarelo claro.
- Logística continua restrita à faixa periférica e recebeu carrinho de materiais adicional.
- Ponte rolante se movimenta na área superior, sem atravessar operadores.
- Copa fica no botão externo; não há painel de Copa cobrindo o Canvas.
- Resumo de ganhos ficou mais compacto e legível.

### Firebase — botão Apoiar
A FINAL 1.0 fazia `tx.get()` no documento diário antes de criar o apoio. Como o documento ainda não existe na primeira tentativa, a regra de leitura não podia validar `resource.data` e o Firestore retornava `PERMISSION_DENIED`.

O patch remove essa leitura. O apoio agora tenta criar diretamente o documento diário. Se já existir, as próprias regras impedem sobrescrever.

## Aplicar
Coloque `aplicar-fix-final-uiux-firebase.mjs` na raiz do projeto (mesma pasta de settings.gradle.kts).

Teste:

    node aplicar-fix-final-uiux-firebase.mjs --dry-run

Aplique:

    node aplicar-fix-final-uiux-firebase.mjs

Depois:

    .\gradlew.bat clean
    .\gradlew.bat assembleDebug

## Firebase Rules
O instalador também atualiza o arquivo local:

    firebase/firestore.rules

Como regras do Firestore ficam no servidor, recomenda-se publicar esse arquivo no Firebase Console:

1. Firebase Console
2. Firestore Database
3. Rules
4. Cole o conteúdo de `firebase/firestore.rules`
5. Publish

Se as regras do seu console já eram exatamente as da FINAL 1.0, o ajuste do código do app é o que resolve o erro de `Apoiar`.

## Restaurar

    node aplicar-fix-final-uiux-firebase.mjs --restore

Backup criado em:

    .patch-backups/final-uiux-fabrica-social-fix-v2/
