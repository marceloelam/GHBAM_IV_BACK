# AGENTS.md — GHBAM IV

## Regras locais do backend Quarkus

- Este diretório é a raiz real do backend Java com Quarkus do GHBAM IV.
- As APIs seguem REST, com persistência JPA/Hibernate sobre PostgreSQL.
- Siga a separação `Resource/Controller`, `Service`, `Repository`, `Entity` e `DTO` conforme o padrão já existente no módulo; não crie camadas paralelas.
- Antes de criar endpoint, entidade ou consulta, procure uma implementação semelhante existente.
- Preserve contratos REST existentes, incluindo URL, método, status e formato de resposta.
- Valide autorização também no backend quando necessária; nunca dependa apenas do RBAC do frontend.
- Utilize o mecanismo de log e auditoria já existente; não crie um sistema paralelo.
- Evite N+1 e não use `SELECT *` em consultas novas.
- Verifique os tipos reais das colunas PostgreSQL antes de escrever consultas e considere campos legados, inclusive datas ou números persistidos como texto.
- Não altere schema sem necessidade e não execute `DROP`, exclusão massiva ou atualização massiva sem solicitação explícita e análise de impacto.
- Não atualize Quarkus, Hibernate, plugins Maven ou outras dependências sem solicitação.
- Não exponha credenciais, tokens, connection strings ou dados clínicos sensíveis.
- Evite builds completos repetidos durante pequenas alterações; prefira validações específicas e faça o build final quando proporcional ao risco.

## Visão geral do projeto

Este repositório contém o **GHBAM IV**, sistema hospitalar e de gestão em saúde utilizado para atendimento, prontuário, internação, enfermagem, prescrições, farmácia, faturamento, relatórios, painéis e demais fluxos assistenciais.

O sistema possui código legado e código moderno convivendo no mesmo ambiente. Qualquer alteração deve preservar o funcionamento existente.

Principais tecnologias:

* Frontend moderno: React com JSX
* Vite
* PrimeReact
* Tailwind CSS quando já utilizado pelo componente
* Backend: Java com Quarkus
* REST APIs
* JPA / Hibernate
* PostgreSQL
* Relatórios: JasperReports
* Sistemas legados: JSF / PrimeFaces
* Servidor legado: Payara
* Autenticação e autorização com RBAC/JWT
* Integrações com sistemas hospitalares e administrativos

---

# REGRA PRINCIPAL

## Trabalhe somente no necessário

Nunca faça uma varredura completa do projeto sem necessidade.

Antes de executar qualquer tarefa:

1. Leia somente os arquivos diretamente relacionados ao pedido.
2. Localize implementações semelhantes já existentes.
3. Identifique o menor conjunto de arquivos que precisa ser alterado.
4. Evite abrir diretórios inteiros.
5. Evite ler arquivos grandes que não participem diretamente da funcionalidade.
6. Evite reanalisar arquivos já compreendidos na mesma sessão.
7. Não altere arquivos fora do escopo sem necessidade técnica real.

Quando existir funcionalidade semelhante no GHBAM, prefira utilizá-la como padrão.

Exemplos:

* fluxo de Dentista;
* atendimento médico;
* atendimento de enfermagem;
* permissões RBAC;
* impressão de relatórios;
* histórico do paciente;
* gravação de logs;
* componentes de menu;
* modais já existentes.

---

# ECONOMIA DE CONTEXTO E TOKENS

Este é um projeto grande.

Para reduzir consumo de contexto:

* não leia o repositório inteiro;
* não execute buscas genéricas sem necessidade;
* não abra centenas de arquivos para descobrir arquitetura;
* não releia arquivos já analisados;
* use buscas por nome de componente, endpoint, entidade, tabela ou função;
* procure implementações existentes antes de criar novas;
* mantenha as respostas objetivas;
* não reproduza arquivos inteiros na resposta se apenas algumas linhas foram alteradas.

Ao receber uma tarefa, primeiro determine:

> Qual é o menor conjunto de arquivos necessário para executar esta alteração?

Só depois prossiga.

---

# SEGURANÇA DAS ALTERAÇÕES

Nunca remova comportamento existente sem solicitação explícita.

Nunca faça:

* refatoração global não solicitada;
* troca de arquitetura;
* atualização massiva de dependências;
* renomeação generalizada;
* alteração de contratos REST existentes;
* alteração de banco sem analisar impacto;
* alteração de autenticação global sem necessidade;
* alteração de configuração de produção sem solicitação.

Preserve compatibilidade com funcionalidades já existentes.

---

# FRONTEND

O frontend principal utiliza React JSX.

Ao desenvolver:

* mantenha os padrões existentes;
* reutilize componentes;
* reutilize serviços;
* reutilize hooks existentes;
* utilize PrimeReact quando o restante do módulo já utilizar;
* preserve responsividade;
* preserve padrões visuais existentes;
* evite introduzir uma nova biblioteca para algo que já possa ser feito com dependências existentes.

Não converter arquivos JSX para TypeScript sem solicitação.

Não trocar React por outro framework.

---

# MENU E NAVEGAÇÃO

Os itens do menu devem respeitar RBAC.

Antes de adicionar uma opção ao menu:

1. identificar o componente atual responsável pelo menu;
2. verificar como permissões são testadas;
3. seguir exatamente o padrão existente;
4. não criar uma segunda implementação de controle de acesso.

Um item de menu só deve aparecer quando a permissão correspondente estiver válida.

Se a permissão não existir, não exibir o item.

---

# RBAC E SEGURANÇA

O GHBAM utiliza controle de acesso baseado em permissões.

Toda nova funcionalidade deve considerar:

* usuário autenticado;
* unidade;
* profissional;
* perfil;
* permissão;
* ação realizada.

Nunca confiar apenas em bloqueio de frontend.

Quando necessário, validar a autorização também no backend.

Não remover validações existentes.

---

# BACKEND QUARKUS

O backend utiliza Java e Quarkus.

Seguir a arquitetura já existente do projeto.

Antes de criar uma nova classe, verificar se existe estrutura semelhante.

Preferir os padrões existentes para:

* Resource/Controller;
* Service;
* Repository;
* Entity;
* DTO;
* Mapper;
* validações;
* tratamento de erro.

Evitar criar camadas novas se o módulo existente não utiliza esse padrão.

---

# APIs REST

Preservar endpoints existentes.

Antes de criar um endpoint novo:

1. procurar endpoint semelhante;
2. verificar convenção de URL;
3. verificar padrão de retorno;
4. verificar DTOs utilizados;
5. verificar autenticação;
6. verificar logging;
7. verificar tratamento de erros.

Não alterar o formato de respostas existentes sem necessidade.

---

# POSTGRESQL

Banco principal: PostgreSQL.

Antes de criar ou alterar consultas:

* verificar os tipos reais das colunas;
* evitar casts desnecessários;
* analisar JOINs;
* evitar SELECT * em consultas novas;
* evitar consultas N+1;
* considerar índices existentes;
* preservar compatibilidade com dados antigos.

Ao utilizar datas, considerar possíveis diferenças entre:

* date;
* timestamp;
* varchar legado.

Nunca presumir que uma coluna antiga possui tipo ideal.

---

# MIGRAÇÕES E ALTERAÇÕES DE BANCO

Não alterar banco automaticamente sem necessidade.

Quando uma funcionalidade exigir nova tabela ou coluna:

1. explicar a alteração;
2. criar SQL/migration conforme padrão existente;
3. evitar perda de dados;
4. fornecer valor default quando necessário;
5. analisar campos NOT NULL;
6. preservar dados existentes.

Nunca executar DROP TABLE, DROP COLUMN ou DELETE massivo sem solicitação explícita.

---

# PRONTUÁRIO DO PACIENTE

Informações clínicas fazem parte do histórico longitudinal do paciente.

Todo novo registro assistencial relevante deve poder ser relacionado, quando aplicável, a:

* paciente;
* BAM;
* prontuário;
* unidade;
* profissional;
* data;
* hora;
* especialidade ou setor;
* atendimento.

O histórico deve acompanhar o paciente mesmo que ele seja atendido em outra unidade.

A unidade onde o atendimento ocorreu deve continuar identificável.

---

# HISTÓRICO DO PACIENTE

Ao implementar novos módulos clínicos:

* integrar os registros ao histórico completo;
* permitir visualização cronológica;
* informar unidade;
* informar profissional;
* informar data/hora;
* informar tipo de atendimento;
* preservar registros anteriores.

Nunca ocultar histórico apenas porque o paciente mudou de unidade.

---

# LOG DO SISTEMA

Operações importantes precisam ser registradas.

Quando aplicável, registrar:

* inclusão;
* alteração;
* exclusão;
* impressão;
* finalização;
* cancelamento;
* mudança de status;
* acesso a funcionalidades clínicas críticas.

O log deve identificar, quando disponível:

* usuário;
* profissional;
* ação;
* data;
* hora;
* paciente;
* BAM;
* unidade;
* módulo.

Utilizar o mecanismo de log já existente no GHBAM.

Não criar outro sistema de log paralelo.

---

# AUDITORIA

Registros clínicos devem preservar rastreabilidade.

Não sobrescrever informações clínicas históricas silenciosamente.

Quando o padrão do sistema permitir, registrar alterações.

Evitar exclusão física de registros clínicos quando uma situação puder ser tratada por status, cancelamento ou inativação.

---

# IMPRESSÕES E JASPERREPORTS

O GHBAM utiliza JasperReports.

Novas impressões devem seguir o padrão visual existente.

Quando aplicável, utilizar:

* logotipo institucional;
* cabeçalho;
* identificação da unidade;
* identificação do paciente;
* BAM/prontuário;
* profissional;
* data e hora;
* paginação.

Antes de criar novo relatório, localizar um relatório semelhante e reutilizar seu padrão.

Evitar duplicar estilos de relatório.

---

# DADOS CLÍNICOS

Não inventar informações clínicas.

Campos sem informação devem permanecer vazios, nulos ou seguir o comportamento já utilizado no sistema.

Não preencher automaticamente diagnóstico, procedimento ou medicamento sem regra explícita.

---

# MÓDULOS MULTIDISCIPLINARES

Para profissionais multidisciplinares:

* respeitar permissão específica;
* identificar o profissional autenticado;
* identificar a especialidade;
* encaminhar o paciente para o fluxo correspondente;
* evitar enviar pacientes multidisciplinares para listas médicas quando não aplicável;
* integrar o registro ao prontuário geral.

Quando possível, usar como referência o fluxo implementado para Dentista ou outro atendimento especializado existente.

---

# LISTAS DE ATENDIMENTO

Ao implementar uma nova modalidade de atendimento, verificar cuidadosamente:

* atmedico;
* atmedicoagenda;
* filas existentes;
* status do atendimento;
* permissões;
* unidade;
* especialidade.

Não inserir automaticamente o paciente em listas médicas se o atendimento pertencer exclusivamente a outro fluxo profissional.

---

# UNIDADE

O GHBAM pode trabalhar com várias unidades.

Nunca assumir que o atendimento atual ocorreu na unidade principal.

Sempre utilizar a unidade vinculada ao atendimento ou usuário quando essa informação estiver disponível.

Registros históricos devem manter a unidade original.

---

# IDENTIFICAÇÃO DO PROFISSIONAL

Evitar solicitar manualmente informações que já estão disponíveis no login.

Quando possível, obter do usuário autenticado:

* nome;
* matrícula;
* especialidade;
* conselho;
* unidade;
* perfil.

Nunca confiar em dados de profissional enviados apenas pelo navegador quando eles puderem ser derivados do token ou sessão autenticada.

---

# TRATAMENTO DE ERROS

Nunca esconder exceções importantes.

No backend:

* utilizar códigos HTTP adequados;
* retornar mensagem compreensível;
* registrar erro técnico quando necessário;
* evitar retornar stack trace para o frontend.

No frontend:

* apresentar mensagem amigável;
* não deixar tela travada;
* finalizar estados de loading mesmo em erro.

---

# PERFORMANCE

Evitar requisições repetidas desnecessárias.

Antes de criar um novo endpoint, verificar se os dados já estão disponíveis em outro endpoint.

Evitar:

* múltiplas consultas iguais;
* loops realizando chamadas REST;
* N+1 no banco;
* recarregamento completo da tela sem necessidade;
* chamadas duplicadas após login.

---

# CACHE

O projeto possui mecanismos que podem utilizar fetch customizado e cache/no-store.

Não alterar comportamento global de cache para corrigir apenas um endpoint.

Investigar primeiro o componente ou serviço específico.

---

# AUTENTICAÇÃO

O sistema pode utilizar:

* JWT;
* LDAP/Active Directory;
* autenticação interna;
* RBAC.

Não modificar autenticação global sem solicitação explícita.

Não armazenar tokens em locais inseguros.

Não imprimir token, senha ou credenciais em logs.

---

# CREDENCIAIS

Nunca incluir no código:

* senhas;
* tokens;
* secrets;
* connection strings de produção;
* chaves privadas;
* credenciais LDAP;
* chaves de serviços externos.

Utilizar variáveis de ambiente ou mecanismo já existente.

---

# INTEGRAÇÕES EXTERNAS

Antes de criar integração nova, verificar se já existe serviço semelhante.

Não introduzir SDK ou dependência grande quando uma chamada HTTP simples resolver.

Adicionar timeout e tratamento de falha.

Uma indisponibilidade externa não deve derrubar todo o GHBAM quando isso puder ser evitado.

---

# CÓDIGO LEGADO

Existem partes em JSF/PrimeFaces e Payara.

Não modernizar automaticamente código legado.

Alterar somente quando a tarefa exigir.

Preservar compatibilidade.

---

# TESTES

Sempre que possível:

* testar regras importantes;
* testar endpoints alterados;
* testar validações;
* testar permissões;
* testar comportamento em erro.

Não criar dezenas de testes triviais apenas para aumentar cobertura.

Priorizar regras clínicas e regras de negócio.

---

# BUILD

Antes de considerar uma tarefa concluída:

Frontend, quando aplicável:

* verificar imports;
* verificar JSX;
* verificar erros de lint/build quando possível.

Backend, quando aplicável:

* verificar compilação;
* verificar imports;
* verificar tipos;
* verificar endpoints;
* verificar entidades/DTOs.

Evitar executar build completo repetidamente durante pequenas alterações.

Executar no momento apropriado.

---

# NÃO ALTERAR DEPENDÊNCIAS SEM NECESSIDADE

Não atualizar automaticamente:

* Quarkus;
* React;
* PrimeReact;
* JasperReports;
* PostgreSQL driver;
* Hibernate;
* Maven plugins;
* npm packages.

Somente alterar versões quando solicitado ou quando for indispensável para a funcionalidade.

---

# GIT

Fazer alterações pequenas e relacionadas.

Evitar misturar:

* refatoração;
* formatação global;
* nova funcionalidade;
* atualização de dependências;

no mesmo conjunto de mudanças.

Não sobrescrever alterações do desenvolvedor.

---

# FORMATAÇÃO

Não reformatar um arquivo inteiro quando modificar apenas poucas linhas.

Isso reduz conflitos de Git e facilita revisão.

Preservar o padrão existente do arquivo.

---

# NOVAS FUNCIONALIDADES

Antes de implementar uma funcionalidade maior, seguir esta ordem:

1. entender o requisito;
2. procurar implementação semelhante;
3. identificar arquivos necessários;
4. identificar banco/tabelas envolvidos;
5. verificar RBAC;
6. verificar log;
7. verificar histórico do paciente;
8. verificar impressão;
9. implementar;
10. validar build/testes;
11. informar exatamente o que foi alterado.

---

# RESPOSTA DO CODEX AO FINAL DA TAREFA

Ao finalizar, responder de forma objetiva.

Utilizar preferencialmente:

## Alterado

Informar os principais arquivos modificados e o que foi feito.

## Banco

Informar se houve alteração de banco.

Se não houve:

> Nenhuma alteração de banco.

## Segurança/RBAC

Informar se permissões foram alteradas.

## Testes

Informar quais verificações foram realizadas.

## Pendências

Informar somente pendências reais.

Não fornecer uma explicação gigantesca de todo o código.

---

# QUANDO A TAREFA FOR GRANDE

Não começar alterando dezenas de arquivos.

Primeiro localizar a implementação existente que mais se aproxima do requisito.

Depois trabalhar incrementalmente.

Exemplo:

Se a tarefa for:

> Criar atendimento multidisciplinar.

Primeiro localizar:

* fluxo do Dentista;
* menu;
* RBAC;
* fila;
* histórico;
* logs.

Somente depois implementar.

---

# BUSCAS NO REPOSITÓRIO

Prefira buscas específicas.

Bom:

```text
buscar "atmedicoagenda"
buscar "Dentista"
buscar componente AppSidebar
buscar endpoint relacionado ao BAM
buscar entidade Log
```

Evitar:

```text
ler toda pasta src
analisar todo frontend
analisar todo backend
ler todos os componentes
```

---

# PRINCÍPIO DE MENOR ALTERAÇÃO

Se uma alteração pode ser resolvida modificando 2 arquivos, não modificar 10.

Se uma função existente pode ser reutilizada, não criar outra.

Se um endpoint existente resolve o problema, não criar um segundo endpoint.

Se um padrão existente funciona, seguir esse padrão.

---

# PRESERVAÇÃO DO GHBAM

O GHBAM IV é um sistema hospitalar em produção e possui funcionalidades acumuladas ao longo de vários anos.

Portanto:

* estabilidade é mais importante que elegância arquitetural;
* compatibilidade é mais importante que modernização desnecessária;
* alterações pequenas são preferíveis a refatorações amplas;
* dados clínicos nunca devem ser colocados em risco;
* não remover comportamento existente sem compreender seu impacto.

---

# REGRA FINAL

Antes de qualquer alteração pergunte internamente:

> Existe uma implementação semelhante no GHBAM que posso reutilizar?

Depois:

> Quais são os poucos arquivos que realmente preciso ler e alterar?

E finalmente:

> Esta alteração preserva banco, histórico, RBAC, logs e funcionalidades existentes?

Somente então implemente.
