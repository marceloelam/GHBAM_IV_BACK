# Evidências TDD — maturidade do backend

Data: 29-07-2026

## Ciclo 1 — contratos HTTP GET

- RED: `ce7f219 test: reproduzir corpos indevidos em endpoints get`
- Falha observada: 46 parâmetros de corpo sem anotação de binding em métodos `@GET`.
- GREEN: `5a37b44 fix: remover corpos de requisicao dos endpoints get`
- Resultado: contrato aprovado e ausência do aviso `GET request should not have a body`.

## Ciclo 2 — cabeçalhos defensivos

- RED: `65bb0b3 test: reproduzir ausencia de cabecalhos defensivos`
- Falha observada: `SecurityHeadersFilter` inexistente.
- GREEN: `887dca8 security: endurecer respostas e externalizar credencial zamed`
- Resultado: rotas JAX-RS retornam `nosniff`, `DENY`, política de referência,
  política de frame/base e `Cache-Control: no-store`.

## Ciclo 3 — alteração de laudo e encaminhamento NIR

- Jornada: ao alterar um laudo inexistente, o especialista deve receber 404 em
  vez de erro interno.
- RED: `3fac3b7 test: reproduzir erro ao alterar laudo inexistente`
- Falha observada: era lançada `IllegalStateException` pelo acesso estático
  Panache, em vez de `NotFoundException`.
- GREEN: `ad64ecc fix: retornar 404 ao alterar laudo inexistente`
- Garantias adicionais: o registro só é sinalizado ao NIR quando exatamente um
  tratamento do BAM é atualizado; zero ou múltiplos tratamentos interrompem a
  operação sem sinalização parcial.
- Comando focal: `.\mvnw.cmd -Dtest=LaudoAutorizacaoServiceTest test`
- Resultado focal: 4 testes, 0 falhas.

## Verificação final

Comando:

```text
.\mvnw.cmd clean package
```

Resultado:

- 53 testes executados;
- 0 falhas;
- 0 erros;
- 0 ignorados;
- JAR executável gerado com sucesso.

O conjunto cobre contratos de migração/schema, GET sem corpo, internação/NIR,
janela de pacientes recentes, resumo clínico, RBAC, permissões, cache de
requisições, unidade, sessões e cabeçalhos HTTP.

## Cobertura medida

O JaCoCo foi integrado ao lifecycle `verify` e ao pipeline. Baseline atual:

- linhas: 4,39%;
- instruções: 6,39%;
- desvios: 4,72%;
- pacote `br.com.iv.security`: 46,98% de linhas;
- pacote `br.gov.secure`: 66,41% de linhas.

O gate inicial exige pelo menos 4% de linhas e impede regressão abaixo do estado
atual. Esse valor não é uma meta de qualidade; deve ser elevado progressivamente.
Os testes atuais são principalmente de contrato e regressão dos fluxos críticos.
Testes de integração de controllers/repositories e E2E autenticado continuam
necessários antes de classificar o sistema como plenamente maduro.
