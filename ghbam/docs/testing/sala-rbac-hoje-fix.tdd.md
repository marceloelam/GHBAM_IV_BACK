# Correção TDD — RBAC da consulta de salas da tela Hoje

## Jornada

Como médico ou profissional da triagem autenticado, quero carregar setores/salas da minha unidade na tela Hoje sem precisar ter permissão administrativa de cadastro de salas.

## RED

Comando:

```bash
.\mvnw.cmd -q "-Dtest=RbacRequestFilterTest,SalaControllerTest" test
```

Resultado inicial:

```text
SalaControllerTest.deveMapearParametroLocalDasRotasDeConsulta expected: <true> but was: <false>
RbacRequestFilterTest.devePermitirConsultaDeSalasDaUnidadeParaQualquerUsuarioAutenticado expected: <true> but was: <false>
```

## GREEN

Implementado:

- `GET /sala/unidade/...` agora é rota de usuário autenticado no RBAC, sem exigir `pcadsalas`/`kadm`;
- `POST/PUT/DELETE /sala` continuam restritos pelo catálogo;
- `SalaController` agora injeta corretamente `@PathParam("local")` nas rotas:
  - `/sala/unidade/{unidade}/local/{local}`;
  - `/sala/unidade/{unidade}/sala/{local}`.

Comandos:

```bash
.\mvnw.cmd -q "-Dtest=RbacRequestFilterTest,SalaControllerTest" test
.\mvnw.cmd test
```

Resultado:

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Garantia

O fluxo da tela Hoje pode consultar salas/setores por unidade com um token autenticado comum, enquanto as operações administrativas de cadastro de sala permanecem protegidas.
