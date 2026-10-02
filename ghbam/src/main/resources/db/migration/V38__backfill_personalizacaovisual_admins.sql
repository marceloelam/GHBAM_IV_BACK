-- A rota "tema-visual" deixou de liberar acesso automaticamente para
-- administradores (kadm); agora depende so da permissao explicita
-- servidor2.personalizacaovisual (ver RoutePermissionCatalog e AppSidebar).
-- Este backfill liga a permissao para os administradores que ja tinham
-- acesso implicito via kadm, preservando o comportamento deles, exceto a
-- matricula '1', que foi desabilitada de proposito em /servidores2.
UPDATE servidor2 s2
SET personalizacaovisual = TRUE
FROM servidor s
WHERE s.matricula = s2.matricula
  AND s.administracao = TRUE
  AND s2.matricula <> '1';
