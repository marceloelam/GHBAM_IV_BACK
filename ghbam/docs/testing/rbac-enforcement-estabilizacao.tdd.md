# Estabilizacao do RBAC com enforcement ativo

Data: 2026-07-26

## Defeito reproduzido (RED)

- Teste: `SecurityPermissionServiceTest.devePreservarPermissoesOperacionaisCadastradasNoServidor2`
- Resultado inicial: falha em `pcaddrives`; a permissao estava ativa em
  `Servidor2`, mas era devolvida como falsa pelo RBAC.
- Teste: `RbacRequestFilterTest.deveManterRbacAtivoPorPadraoNoFiltro`
- Resultado inicial: falha porque o `defaultValue` do filtro ainda era `false`,
  apesar de `application.properties` configurar o padrao como `true`.

## Correcao (GREEN)

- Incluidas no resolvedor as permissoes de `Servidor2`:
  `pcaddrives`, `panotacoesenfermagem`, `pmonitoramento`,
  `pcontroleplantoes`, `pprodutividaderh`, `ptreinamentocertificacao` e
  `pgerenciarcertificados`.
- Adicionado teste de consistencia que impede o catalogo RBAC de referenciar
  uma permissao inexistente no resolvedor do login.
- Alinhado o filtro e o arquivo de configuracao para enforcement ativo por
  padrao (`true`).
- Removidos delimitadores Markdown indevidos do arquivo
  `application.properties`.

## Evidencias

- Testes focados:
  `RbacRequestFilterTest,RoutePermissionCatalogTest,SecurityPermissionServiceTest`
  - resultado: sucesso.
- Suite completa:
  - 26 testes executados;
  - 0 falhas;
  - 0 erros;
  - 0 ignorados.

## Escopo preservado

- Nenhum arquivo do Electron do painel de chamada foi alterado.
- As operacoes publicas estritamente necessarias ao painel kiosk continuam
  cobertas por testes.
- Nenhuma alteracao foi implantada em producao durante esta etapa.
