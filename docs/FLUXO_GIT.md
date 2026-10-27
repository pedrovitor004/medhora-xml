# Fluxo de branches desta entrega

`main` → `develop` → branches de trabalho → `develop` → `main`.

- `feature/medhora-xml`: recursos visuais, dados em memória, telas e navegação XML (24 commits).
- `feature/lembretes-e-voz`: notificações locais, alarmes, fala e configurações (6 commits).
- `fix/cache-kotlin-windows`: configuração de compilação para contornar o bloqueio do cache incremental (1 commit).
- Documentação integrada em `develop` (1 commit).

São 32 commits de conteúdo, sem commits vazios. Os merges usam `--no-ff` para manter as branches visíveis no histórico. A entrega completa integra alterações que já estavam no diretório de trabalho; os commits intermediários não são versões testadas ou declaradas compiláveis isoladamente.

Nenhum build, teste ou emulador foi executado durante a organização do histórico. Logs, caches locais, APKs, diretórios de build e configurações locais não são incluídos. As branches são publicadas no remoto `origin`, junto da `main` integrada, sem force push.
