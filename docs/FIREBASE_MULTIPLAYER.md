# Firebase / multiplayer assíncrono

A edição 1.0 adiciona uma camada social opcional sem transformar a economia local em dependência da nuvem.

## Recursos

- Criação de personagem próprio.
- Firebase Authentication anônimo.
- Perfil público do dono da oficina.
- Ranking por reputação.
- Status recente de presença (`lastSeenAt`).
- Visualização de nível, máquinas e produção/10 min de outros jogadores.
- Apoio entre jogadores: cada jogador pode enviar 1 impulso (+10 min) por dia para cada outro jogador.
- O avatar local aparece também dentro da Fábrica Viva.

## Ativação

1. Crie um projeto em https://console.firebase.google.com/.
2. Adicione um aplicativo Android com package `br.com.usinagemmaster`.
3. Baixe `google-services.json` e coloque em `app/google-services.json`.
4. Em Authentication, habilite o provedor **Anonymous**.
5. Em Firestore Database, crie o banco.
6. Publique `firebase/firestore.rules` e `firebase/firestore.indexes.json`.

Com Firebase CLI instalado, na raiz do projeto:

```bash
firebase login
firebase use SEU_PROJECT_ID
firebase deploy --only firestore:rules,firestore:indexes
```

## Segurança

As regras incluídas impedem um jogador de editar o perfil de outro e limitam a leitura/resgate de apoios aos envolvidos. A economia principal continua local/offline.

Para um ranking competitivo com anti-cheat real, a evolução natural é mover validação de reputação/produção para um backend autoritativo (por exemplo Cloud Functions) e ativar App Check/Play Integrity. A versão 1.0 trata o ranking como recurso social, não como competição com prêmio real.
