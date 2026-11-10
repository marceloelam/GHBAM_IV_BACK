package br.com.iv.security;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RoutePermissionCatalog {

    private final Map<String, List<String>> routePermissions = Map.ofEntries(
            entry("servidor/atualizar", "kadm"),
            entry("servidor/remover", "kadm"),
            entry("servidor/idser", "kadm"),
            entry("servidor/matricula", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento",
                    "klaboratorio", "krx", "ktomografia", "kmedicacao", "kfarmaciacentral", "kremedios",
                    "kconsultorio_admin", "kconsultorio_recepcao", "kconsultorio_medico", "kconsultorio_financeiro",
                    "krh", "kadm"),
            // Lista de servidores da unidade: o plano de acao do NSP escolhe o
            // responsavel a partir dela, sem liberar o resto do recurso servidor.
            entry("servidor/unidade", "kadm", "plistausuarios", "pnspclassificar"),
            entry("servidor", "kadm", "plistausuarios"),
            entry("servidor2/matricula", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento",
                    "klaboratorio", "krx", "ktomografia", "kmedicacao", "kfarmaciacentral", "kremedios",
                    "kconsultorio_admin", "kconsultorio_recepcao", "kconsultorio_medico", "kconsultorio_financeiro",
                    "krh", "kadm"),
            entry("servidor2", "kadm", "ksubadm"),
            // Coordenacao de setor: o coordenador altera acessos da propria equipe (validado no recurso);
            // definir coordenadores e equipes e so da administracao.
            entry("coordenacao-setor/gestao", "kadm"),
            // Liberacao por segunda pessoa (coordenador do NIR) em Cancelar alta / Restaurar paciente.
            entry("coordenacao-setor/autorizar-coordenador", "pnirrestaurarinternacao", "prestaurarpaciente", "kadm"),
            entry("coordenacao-setor/autorizar-medico", "pnirrestaurarinternacao", "prestaurarpaciente", "kadm"),
            entry("coordenacao-setor", "pcoordenadorsetor", "kadm"),
            entry("salas", "pcadsalas", "kadm"),
            entry("setores", "pcadsetores", "kadm"),
            entry("unidades", "pcadunidades", "kadm"),
            entry("ad", "kadm", "ksubadm"),
            entry("admin/pool-status", "kadm"),
            entry("log/data", "kadm", "plogs", "pauditoriaclinica"),
            entry("log/pesquisa", "kadm", "plogs", "pauditoriaclinica"),
            entry("log/foto", "kadm", "plogs", "pauditoriaclinica"),
            entry("log", "kadm", "plogs"),
            entry("producao-medica", "pproducaomedica", "kadm"),
            entry("producao-assistencial", "kadm"),
            entry("api/assinaturas-clinicas", "kmedico", "kdentista", "kenfermagem", "ktcenfermagem", "kconsultorio_medico", "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia", "pevolucaomultidisciplinar", "pverificarassinatura", "kadm"),
            entry("api/odontologia", "kdentista"),
            // GHBAM APS (UBS/ESF - substituto do PEC e-SUS). Checagem fina por
            // perfil (profissional, ACS, sala de vacina, gestao) nos controllers.
            entry("api/aps", "paps", "papsacs", "papsvacina", "papsgestao", "kadm"),
            entry("api/assistant", "kmedico", "kenfermagem", "ktriagem", "kadm"),
            entry("api/fingerprint", "krecepcao", "ktriagem", "kadm"),
            entry("api/hf", "ktriagem", "kmedico", "kenfermagem", "kfisioterapia",
                    "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia",
                    "pevolucaomultidisciplinar", "kadm"),
            entry("api/ia", "kmedico", "kenfermagem", "ktriagem", "kadm"),
            entry("ditado", "kmedico", "kenfermagem", "kadm", "kdentista", "kfisioterapia",
                    "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia"),
            entry("api/usuario-logado", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kadm"),
            entry("api/usuarios-conectados", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento", "pchatprofissionais", "kadm", "ksubadm"),
            entry("bulario", "kmedico", "kenfermagem", "ktriagem", "kadm"),
            entry("cbo", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento", "kadm"),
            entry("cid", "ktriagem", "kmedico", "kenfermagem", "kfaturamento", "kadm"),
            // Conferencia da embalagem na checagem a beira-leito: quem administra e,
            // na maioria das vezes, o tecnico de enfermagem (so leitura do catalogo).
            entry("cdss/conferencia-medicamento", "kenfermagem", "ktcenfermagem", "kmedico", "kadm"),
            // Analise de imagem por IA (MONAI): a IA sugere, o medico da ciencia.
            // Desligado por padrao; so medico/enfermagem analisam e consultam.
            entry("imagem-ia/analisar", "kmedico", "krx", "ktomografia", "kadm"),
            entry("imagem-ia/analisar-exame", "kmedico", "krx", "ktomografia", "kadm"),
            entry("imagem-ia/ciencia", "kmedico", "kenfermagem", "kadm"),
            entry("imagem-ia/status", "kadm"),
            entry("imagem-ia", "kmedico", "kenfermagem", "ktcenfermagem", "krx", "ktomografia", "kadm"),
            // Painel do plantao: so leitura, internados da unidade da sessao.
            entry("cdss/plantao", "kenfermagem", "ktcenfermagem", "kmedico", "pnir", "penfermariaseleitos", "kadm"),
            // Dispositivos-dia (denominador de IRAS): enfermagem e CCIH.
            entry("cdss/plantao/dispositivos/indicadores", "kenfermagem", "pccih", "kadm"),
            // Indicadores de seguranca do paciente: NSP, CCIH e gestao.
            entry("cdss/indicadores-seguranca", "pnsp", "pnspgestao", "pccih", "kadm"),
            // Limites de dose: todos os clinicos consultam; so pregrasdose/kadm editam e validam.
            entry("cdss/regras-dose/gestao", "kmedico", "kenfermagem", "ktcenfermagem", "kfisioterapia", "knutricao",
                    "kfonoaudiologia", "kpsicologia", "kassistencia", "pevolucaomultidisciplinar", "kfarmaciacentral", "pfarmaciaavaliacao",
                    "pregrasdose", "pnsp", "pccih", "kadm"),
            entry("cdss/regras-dose/historico", "kmedico", "kenfermagem", "ktcenfermagem", "kfisioterapia", "knutricao",
                    "kfonoaudiologia", "kpsicologia", "kassistencia", "pevolucaomultidisciplinar", "kfarmaciacentral", "pfarmaciaavaliacao",
                    "pregrasdose", "pnsp", "pccih", "kadm"),
            entry("cdss/regras-dose/editar", "pregrasdose", "kadm"),
            entry("cdss/regras-dose/incluir", "pregrasdose", "kadm"),
            entry("cdss/regras-dose/excluir", "pregrasdose", "kadm"),
            entry("cdss/regras-dose/restaurar", "pregrasdose", "kadm"),
            // Sugerir: qualquer clinico (medico, enfermagem, tecnico, multi, farmacia).
            entry("cdss/regras-dose/sugerir", "kmedico", "kenfermagem", "ktcenfermagem", "kfisioterapia", "knutricao",
                    "kfonoaudiologia", "kpsicologia", "kassistencia", "pevolucaomultidisciplinar", "kfarmaciacentral",
                    "pfarmaciaavaliacao", "pregrasdose", "kadm"),
            entry("cdss/regras-dose/sugestoes", "kmedico", "kenfermagem", "ktcenfermagem", "kfisioterapia", "knutricao",
                    "kfonoaudiologia", "kpsicologia", "kassistencia", "pevolucaomultidisciplinar", "kfarmaciacentral",
                    "pfarmaciaavaliacao", "pregrasdose", "pnsp", "pccih", "kadm"),
            entry("cdss/regras-dose/sugestoes/decidir", "pregrasdose", "kadm"),
            entry("cdss", "kmedico", "kenfermagem", "ktriagem", "kfarmaciacentral", "kfarmcentral", "kfarmhmsfx",
                    "pauditoriaclinica", "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto",
                    "penfermariaseleitos", "pnir", "pccih", "kadm"),
            entry("configuracaounidade/atualizar", "pcadunidades", "kadm"),
            entry("configuracaounidade/remover", "pcadunidades", "kadm"),
            entry("configuracaounidade", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento", "kfarmaciacentral", "pcadunidades", "kadm"),
            entry("integracao-sus", "pintegracaosus", "kadm"),
            entry("ocorrencia-sistema", "kadm", "kfaturamento"),
			entry("vigilancia-epidemiologica/configuracao", "pconfiguracaovigilanciaepidemiologica", "pccih", "kadm"),
			// Mapa e acumulos sao so agregados (regiao >= 3 casos, diagnostico >= 5):
			// medico e enfermagem veem o alerta do cabecalho sem acessar a configuracao.
			entry("vigilancia-epidemiologica/alerta", "kmedico", "kenfermagem", "ktcenfermagem", "kconsultorio_medico", "pvigilanciaepidemiologica", "pccih", "kadm"),
			entry("vigilancia-epidemiologica/mapa", "kmedico", "kenfermagem", "ktcenfermagem", "kconsultorio_medico", "pvigilanciaepidemiologica", "pccih", "kadm"),
			entry("vigilancia-epidemiologica/emergentes", "kmedico", "kenfermagem", "ktcenfermagem", "kconsultorio_medico", "pvigilanciaepidemiologica", "pccih", "kadm"),
			entry("vigilancia-epidemiologica", "pvigilanciaepidemiologica", "pccih", "kadm"),
            entry("notificacoes-sinan", "psinan", "pvigilanciaepidemiologica", "pccih", "kadm"),
            entry("evolucao-sistema", "kadm"),
            entry("tema-visual", "ppersonalizacaovisual"),
            entry("pacs", "ppacs", "kadm"),

            // Centro Cirurgico. As chaves mais longas resolvem primeiro, entao as
            // sub-rotas (salas/lista-espera/execucao/documentacao) tem seu proprio
            // par de permissoes e a raiz cobre o mapa cirurgico e o agendamento.
            entry("centro-cirurgico/salas", "pcirurgiasalas", "pcentrocirurgico", "kmedico", "kenfermagem", "kadm"),
            entry("centro-cirurgico/lista-espera", "pcirurgialistaespera", "pcirurgiaagendar", "pcentrocirurgico",
                    "kmedico", "kenfermagem", "kadm"),
            entry("centro-cirurgico/execucao", "pcirurgiaexecutar", "pcirurgiaopme", "pcentrocirurgico", "kmedico",
                    "kenfermagem", "kadm"),
            entry("centro-cirurgico/documentacao", "pcirurgiadescrever", "pcirurgiaanestesia", "pcentrocirurgico",
                    "kmedico", "kenfermagem", "kadm"),
            entry("centro-cirurgico", "pcirurgiaagendar", "pcirurgiasuspender", "pcentrocirurgico", "kmedico",
                    "kenfermagem", "kadm"),
            entry("assistencial/cme", "pcme", "pcentrocirurgico", "kadm"),
            entry("cme/rastreio", "pcme", "pcentrocirurgico", "kadm"),
            entry("almoxarifado/setor", "palmoxpedir", "palmoxarifado", "kadm"),
            entry("almoxarifado", "palmoxarifado", "kadm"),
            entry("assistencial/hemoterapia", "phemoterapia", "kenfermagem", "kmedico", "kadm"),
            entry("assistencial/snd", "psnd", "knutricao", "kenfermagem", "kadm"),
            entry("assistencial/rnds", "prnds", "pintegracaosus", "kadm"),
            // Acesso restrito de proposito: nenhum perfil clinico nem kadm libera
            // automatico. So pnsp/pnspgestao/pnspclassificar, marcados individualmente
            // no cadastro do servidor.
            entry("nsp/eventos", "pnsp", "pnspgestao", "pnspclassificar"),
            entry("diagnostico-sistema", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento",
                    "klaboratorio", "krx", "ktomografia", "kmedicacao", "kfarmaciacentral", "kremedios",
                    "kconsultorio_admin", "kconsultorio_recepcao", "kconsultorio_medico", "kconsultorio_financeiro",
                    "presumopaciente", "kadm"),
            entry("escaladeenfermagem", "kenfermagem", "pescalaenfermagem", "kadm"),
            entry("escalamedica", "kmedico", "kcriaragenda", "kadm"),
            entry("escalapaciente", "krecepcao", "kmedico", "kagendarpaciente", "kconsultorio_admin", "kconsultorio_recepcao", "kconsultorio_medico", "kadm"),
            entry("consultorioagenda", "kconsultorio_admin", "kconsultorio_recepcao", "kconsultorio_medico", "kconsultorio_financeiro", "krecepcao", "kmedico", "kadm"),
            entry("configuracaoagendaconsultorio", "kconsultorio_admin", "kconsultorio_recepcao", "kadm"),
            entry("financeiroconsultorio", "kconsultorio_admin", "kconsultorio_financeiro", "kadm"),
            entry("procedimentoconsultorio", "kconsultorio_admin", "kconsultorio_financeiro", "kadm"),
            entry("evolucaopacientereceituario", "kmedico", "kadm"),
            entry("genai", "kmedico", "kenfermagem", "ktriagem", "kadm"),
            entry("rag2/prontuario", "pragprontuario"),
            entry("resumo-clinico", "presumoclinicoia", "kadm"),
            entry("limparpainel", "krecepcao", "ktriagem", "kadm"),
            entry("parecerespecialista", "kmedico", "kadm"),
            entry("procedimentos", "kfaturamento", "krx", "kadm"),
            entry("procedimentospasrv", "kfaturamento", "kadm"),
            entry("prontuario-clinico/alergia-triagem", "ktriagem", "kenfermagem", "kmedico", "kadm"),
            // Quem prescreve ou administra medicacao precisa ver a alergia, sem acesso aos demais itens do prontuario.
            entry("prontuario-clinico/alergias-ativas", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente", "kadm", "kmedicacao",
                    "ktcenfermagem", "kconsultorio_medico"),
            entry("prontuario-clinico", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente", "kadm"),
            entry("protocolos", "kmedico", "kenfermagem", "ktriagem", "kadm"),
            entry("protocolos-execucao", "kmedico", "kenfermagem", "ktriagem", "kadm"),
            entry("receitas", "kmedico", "kenfermagem", "kadm"),
            // ktcenfermagem (tecnico de enfermagem) precisa imprimir/ver os mesmos
            // relatorios do dia a dia (evolucao de sinais, BAM) que kenfermagem ja
            // acessa por essa rota generica de geracao de relatorio.
            entry("report", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "ktcenfermagem", "kfaturamento", "kadm"),
            entry("sala", "pcadsalas", "kadm"),
            entry("upload", "kadm"),
            // Mais especifico que "pacientes" abaixo (prefixo mais longo vence) Ã¢â‚¬â€
            // exportacao LGPD e acao administrativa, restrita a kadm, nao aberta
            // pros mesmos papeis clinicos que consultam paciente no dia a dia.
            entry("pacientes/exportar", "kadm", "pexportardadoslgpd"),
            // Correcao de BAM (/base2/correcaodebam): mesclagem/cancelamentos
            // irreversiveis. Sem estas entradas caiam no generico "pacientes",
            // aberto a recepcao/medico/enfermagem. Inclui as rotas GET legadas.
            entry("pacientes/correcao-bam", "kadm", "pcorrecaobam"),
            // Unificacao de cadastros duplicados (Correcao de BAM): move o historico entre pacientes.
            entry("paciente-unificacao", "kadm", "pcorrecaobam"),
            entry("pacientes/correcaobam", "kadm", "pcorrecaobam"),
            entry("pacientes/correcaobam2", "kadm", "pcorrecaobam"),
            entry("pacientes/opcao1", "kadm", "pcorrecaobam"),
            entry("pacientes/opcao2", "kadm", "pcorrecaobam"),
            entry("pacientes/opcao3", "kadm", "pcorrecaobam"),
            entry("pacientes/opcao4", "kadm", "pcorrecaobam"),
            entry("pacientes/opcao5", "kadm", "pcorrecaobam"),
            entry("pacientes/opcao7", "kadm", "pcorrecaobam"),
            // Pedido de exclusao de dados (LGPD art. 18, VI) - sempre negado com base
            // legal (guarda permanente do prontuario, Resolucao CFM 1.821/2007 +
            // LGPD art. 16, II). Mesmo publico de "pacientes/exportar" - setor
            // administrativo/ouvidoria, nao autosservico do paciente.
            entry("lgpd", "kadm", "pexportardadoslgpd"),
            // A fila de triagem recalcula a idade dos pacientes antes da listagem.
            // Mantem ktriagem restrito a este subfluxo, sem ampliar a permissao
            // para as demais alteracoes do recurso generico "pacientes".
            entry("pacientes/triagem", "ktriagem", "kenfermagem", "kmedico", "kadm"),
            entry("pacientes/unidade2", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento", "kadm"),
            // Lista de internados tambem alimenta as telas Multidisciplinar
            // (InternadosEnf.jsx) e Escalas de Enfermagem (EscalaEnfTriagem.jsx),
            // ja liberadas no frontend pra esses papeis - sem isso a tela quebrava
            // na propria carga inicial com 403.
            entry("pacientes/internadonomenir", "krecepcao", "kmedico", "kenfermagem", "kfaturamento",
                    "pbalancohidrico", "penfermariaseleitos", "pnir", "pccih",
                    "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia",
                    "pevolucaomultidisciplinar", "pescalaenfermagem", "kadm", "kfarmaciacentral", "pciclomedicacao"),
            entry("pacientes/internadonomenirtodos", "krecepcao", "kmedico", "kenfermagem", "kfaturamento",
                    "pbalancohidrico", "penfermariaseleitos", "pnir", "pccih", "kadm"),
            entry("pacientes/nomenir2", "krecepcao", "kmedico", "kenfermagem", "kfaturamento",
                    "pbalancohidrico", "penfermariaseleitos", "pnir", "pccih", "pprontuariocompleto", "kadm"),
            // Busca de pacientes pra restaurar internacao (tela NIR Restauracao de
            // Pacientes). Mais especifica que a entrada generica "pacientes" logo
            // abaixo, entao vence no matching por prefixo mais longo. Mesma lista
            // da rota irma "localcenso/localunidade", usada no mesmo fluxo.
            entry("pacientes/nomenirrestaurarinternacao", "pnirrestaurarinternacao", "prestaurarpaciente",
                    "pccih", "pcadleitos", "kadm"),
            entry("pacientes/porid", "krecepcao", "kmedico", "kdentista", "kenfermagem", "kfaturamento",
                    "pbalancohidrico", "penfermariaseleitos", "pnir", "pccih", "pprontuariocompleto",
                    "pevolucaomultidisciplinar", "kadm"),
            entry("pacientes/idpaciente", "krecepcao", "kmedico", "kdentista", "kenfermagem", "kfaturamento",
                    "klaboratorio", "krx", "ktomografia", "kmedicacao", "kadm", "pnsp", "pnspgestao",
                    "pbalancohidrico", "penfermariaseleitos", "pnir", "pccih", "pprontuariocompleto",
                    "pevolucaomultidisciplinar", "kfarmaciacentral", "pciclomedicacao"),
            // Busca do Ciclo de Medicacao: a farmacia localiza o paciente sem acesso ao restante de "pacientes".
            entry("pacientes/recentes", "krecepcao", "kmedico", "kdentista", "kenfermagem", "kfaturamento", "kadm",
                    "pnsp", "pnspgestao", "kfarmaciacentral", "pciclomedicacao"),
            entry("pacientes/por-prontuario", "krecepcao", "kmedico", "kdentista", "kenfermagem", "kfaturamento", "kadm",
                    "pnsp", "pnspgestao", "kfarmaciacentral", "pciclomedicacao"),
            entry("pacientes", "krecepcao", "kmedico", "kdentista", "kenfermagem", "kfaturamento", "kadm",
                    "pnsp", "pnspgestao"),
            entry("bam/idpaciente", "krecepcao", "ktriagem", "kmedico", "kdentista", "kenfermagem", "kfaturamento",
                    "klaboratorio", "krx", "ktomografia", "kmedicacao", "kadm", "pnsp", "pnspgestao",
                    "pbalancohidrico", "penfermariaseleitos", "pnir", "pccih", "pprontuariocompleto",
                    "pevolucaomultidisciplinar", "kfarmaciacentral", "pciclomedicacao"),
            entry("bam/idbam", "krecepcao", "ktriagem", "kmedico", "kdentista", "kenfermagem", "kfaturamento",
                    "klaboratorio", "krx", "ktomografia", "kmedicacao", "kadm", "pnsp", "pnspgestao",
                    "pbalancohidrico", "penfermariaseleitos", "pnir", "pccih", "pprontuariocompleto",
                    "pevolucaomultidisciplinar", "kfarmaciacentral", "pciclomedicacao"),
            entry("bam", "krecepcao", "ktriagem", "kmedico", "kdentista", "kenfermagem", "kfaturamento", "kadm",
                    "pnsp", "pnspgestao"),
            entry("registro", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kadm"),
            entry("avaliacao", "ktriagem", "kenfermagem", "kmedico", "kfaturamento", "presumopaciente",
                    "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos", "pnir", "pccih", "kadm"),
            entry("tratamento", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kadm"),
            entry("atendimentomedico", "ktriagem", "kmedico", "kenfermagem", "krecepcao", "kadm",
                    "pbalancohidrico", "penfermariaseleitos", "pnir", "pccih", "pprontuariocompleto",
                    "pevolucaomultidisciplinar"),
            entry("atendimentomedicoagenda", "kmedico", "kconsultorio_admin", "kconsultorio_medico", "kadm"),
            entry("evolucaopaciente", "ktriagem", "kmedico", "kenfermagem", "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia", "pevolucaomultidisciplinar", "kadm"),
            entry("evolucaopacienteenf", "kenfermagem", "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia", "pevolucaomultidisciplinar", "kadm"),
            entry("sae-enfermagem", "kenfermagem", "psaeenfermagem", "kadm"),
            entry("sumario-obito", "kenfermagem", "ktcenfermagem", "kadm"),
            entry("classificacao-ghbam", "pclassificacaoghbam", "kenfermagem", "kadm", "pclassificacaoghbam_auditar",
                    "pclassificacaoghbam_revisar", "pclassificacaoghbam_aprovar", "pclassificacaoghbam_admin"),
            entry("classificacao-ghbam-plano", "pclassificacaoghbam", "kenfermagem", "ktcenfermagem", "kadm", "pclassificacaoghbam_auditar"),
            entry("controle-assistencial", "kenfermagem", "pcontroleassistencial", "kadm"),

            entry("ged/documentos", "pged", "pgedconsulta", "pgedremover"),
            // Cautela nao herda de pnir nem de kadm: o toggle do cadastro e a unica porta.
            entry("cautela", "pcautela"),
            entry("documentos-assistenciais", "kenfermagem", "kmedico", "kadm", "pdocumentosassistenciais"),
            entry("modeloevolucao", "kmedico", "kadm"),
            entry("modeloprescricao", "kmedico", "kadm"),
            entry("modeloevolucaoenf", "kenfermagem", "pmodeloevolucaoenf", "pevolucaomultidisciplinar", "kadm"),
            entry("modeloprescricaoenf", "pmodeloprescricaoenf", "kadm"),
            // Checar/administrar medicacao prescrita e' a funcao central do tecnico
            // de enfermagem (dentro do escopo legal dele, sob supervisao) - nao e'
            // um ato privativo de enfermeiro como a SAE, por isso ktcenfermagem
            // entra aqui mas nao em toda rota que aceita kenfermagem.
            entry("prescricao/aprazamento-checagem", "kmedico", "kenfermagem", "ktcenfermagem", "kadm"),
            entry("prescricao", "kmedico", "kadm"),

            entry("prescricaocontinuacao", "kmedico", "kenfermagem", "pbalancohidrico",
                    "penfermariaseleitos", "pprontuariocompleto", "kadm"),

            entry("prescricao-itens", "krecepcao", "kmedico", "kenfermagem", "kfarmaciacentral", "kadm"),
            entry("anamneseinternacao", "kmedico", "kenfermagem", "ktriagem", "krecepcao", "kfaturamento",
                    "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos",
                    "pnir", "pccih", "kadm"),
            entry("consultapediatrica", "kmedico", "kenfermagem", "ktriagem", "krecepcao", "kfaturamento",
                    "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos",
                    "pnir", "pccih", "kadm"),
            entry("vacinapediatrica", "kmedico", "kenfermagem", "ktriagem", "krecepcao", "kfaturamento",
                    "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos",
                    "pnir", "pccih", "kadm"),
            entry("atendimentomultidisciplinar/bam", "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia",
                    "kpsicologia", "kfarmaciacentral", "kenfermagem", "pevolucaomultidisciplinar",
                    "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos",
                    "pnir", "pccih", "kadm"),
            entry("atendimentomultidisciplinar/paciente", "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia",
                    "kpsicologia", "kfarmaciacentral", "kenfermagem", "pevolucaomultidisciplinar",
                    "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos",
                    "pnir", "pccih", "kadm"),
            entry("atendimentomultidisciplinar", "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia",
                    "kpsicologia", "kfarmaciacentral", "kenfermagem", "pevolucaomultidisciplinar",
                    "kadm"),
            entry("multidisciplinarpts", "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia",
                    "kpsicologia", "kfarmaciacentral", "kenfermagem", "kadm"),
            entry("multidisciplinarencaminhamento", "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia",
                    "kpsicologia", "kfarmaciacentral", "kenfermagem", "kadm"),
            entry("resumodealta", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente",
                    "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos", "pnir", "pccih", "kadm"),
            entry("resumomedico", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente",
                    "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos", "pnir", "pccih", "kadm"),
            entry("laudoautorizacao", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente",
                    "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos", "pnir", "pccih", "kadm"),
            entry("listaanestesica", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente",
                    "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos", "pnir", "pccih", "kadm"),
            entry("backend_controle_entrada", "kenfermagem", "kmedico", "pbalancohidrico", "kadm"),
            entry("backend_controle_saida", "kenfermagem", "kmedico", "pbalancohidrico", "kadm"),
            entry("backend_resumo_balanco", "kenfermagem", "kmedico", "pbalancohidrico", "kadm"),
            entry("internacao", "kmedico", "kfaturamento", "kadm"),
            entry("jornada-pa/pep", "ktriagem", "kmedico", "kenfermagem", "presumopaciente", "phistoricocompleto_adm", "pprontuariocompleto", "penfermariaseleitos", "pnir", "pccih", "kadm"),
            entry("jornada-pa/bam", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento",
                    "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos",
                    "pnir", "pccih", "kadm"),
            entry("jornada-pa/conformidade", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento",
                    "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos",
                    "pnir", "pccih", "kadm"),
            // Fila gerencial (Pendencias PA): so gestao/faturamento. Nao casa
            // "jornada-pa/pendencias-baixas", que segue a regra de "jornada-pa".
            entry("jornada-pa/pendencias", "kfaturamento", "kadm"),
            entry("jornada-pa", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente", "kadm"),
            // Mesma lista de internados (Multidisciplinar/Escalas) e tambem usada
            // pelo Ciclo de Medicacao no modo farmacia (kfarmaciacentral/pciclomedicacao).
            entry("novoInternado", "kmedico", "kenfermagem", "kfaturamento",
                    "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia",
                    "pevolucaomultidisciplinar", "pescalaenfermagem",
                    "kfarmaciacentral", "pciclomedicacao", "kadm"),
            entry("localcenso/localunidade", "pnirrestaurarinternacao", "prestaurarpaciente", "pccih", "pcadleitos", "kadm"),
            entry("localcenso", "pcadleitos", "kadm"),
            entry("leitoreserva", "pnir", "kfaturamento", "kadm"),
            entry("resumo-alta", "pnir", "kmedico", "kadm"),
            entry("setorcenso", "pcadsetor", "kfaturamento", "kadm"),
            entry("api/pedidos", "kmedico", "kenfermagem", "klaboratorio", "krx", "ktomografia", "kadm"),

            entry("pedidomedico2/bam", "krecepcao", "kmedico", "kenfermagem", "kfaturamento", "klaboratorio",
                    "krx", "ktomografia", "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto",
                    "penfermariaseleitos", "pnir", "pccih", "kadm"),
            // kultrassonografia so existia liberado pro Drive; a tela Ultrassonografia
            // (pedidos + chamada de paciente) nunca teve cobertura nessas duas rotas.
            entry("pedidomedico2", "krecepcao", "kmedico", "kenfermagem", "kfaturamento", "klaboratorio", "krx", "ktomografia", "kultrassonografia", "kadm"),
            entry("pedidoresultadolaboratorio", "klaboratorio", "kmedico", "kenfermagem", "kadm"),
            entry("chamada", "ktriagem", "kmedico", "kenfermagem",
                    "klaboratorio", "krx", "ktomografia", "kultrassonografia", "kmedicacao", "kadm"),
            entry("bi-executivo", "pcadunidades", "kfaturamento", "pbiexecutivo", "kadm"),
            entry("procedimentossus", "kfaturamento", "kadm"),
            entry("procedimentosrx", "kfaturamento", "krx", "kadm"),
            entry("afaturar", "krecepcao", "kfaturamento", "kadm"),
            entry("tiss/contas", "kfaturamento", "kadm"),

            // Central Municipal de Faturamento SUS (Fase 1). Prefixos de acao mais
            // longos (fechar/reabrir/reprocessar/resolver/importar) vem antes dos
            // prefixos genericos para vencer no match por prefixo mais especifico
            // (ver requiredPermissionsFor abaixo).
            entry("faturamento/producao/reprocessar", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao/processar-competencia", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao/classificar-pendentes", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao/sincronizar-sigtap", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao/classificar", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao/corrigir-campo", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao/buscar-cns-cnes", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao/vincular-aih", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao/buscar-cns-paciente", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao/sincronizar-cns", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao/completar-cbo", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao/corrigir-cns-paciente", "kfaturamento", "pfaturamentosuscorrigir", "kadm"),
            entry("faturamento/producao", "kfaturamento", "kadm"),
            entry("faturamento/competencias/fechar", "kfaturamento", "pfaturamentosusfechar", "kadm"),
            entry("faturamento/competencias/retirar", "kfaturamento", "kadm"),
            entry("faturamento/competencias/reabrir", "pfaturamentosusreabrir", "kadm"),
            entry("faturamento/competencias", "kfaturamento", "kadm"),
            entry("faturamento/dashboard", "kfaturamento", "kadm"),
            entry("sigtap/importar-datasus", "sigtapimportar", "kadm"),
            entry("sigtap/importar", "sigtapimportar", "kadm"),
            entry("sigtap/competencias", "kfaturamento", "sigtapimportar", "kadm"),
            entry("sigtap/datasus/competencias", "kfaturamento", "kadm"),
            entry("sigtap", "kfaturamento", "kadm"),
            entry("faturamento/exportacoes/gerar", "kfaturamento", "bpaexportar", "kadm"),
            entry("faturamento/exportacoes", "kfaturamento", "kadm"),
            entry("faturamento/remessas/transmitir", "kfaturamento", "bpaexportar", "kadm"),
            // Cancelar devolve producao para a fila de exportacao: mesma permissao de
            // transmitir, nao a de leitura da lista.
            entry("faturamento/remessas/cancelar", "kfaturamento", "bpaexportar", "kadm"),
            entry("faturamento/remessas/arquivo", "kfaturamento", "kadm"),
            entry("faturamento/remessas", "kfaturamento", "kadm"),

            entry("faturamento/apac/autorizar", "kfaturamento", "apacautorizar", "kadm"),
            entry("faturamento/apac", "kfaturamento", "kadm"),
            entry("faturamento/raas", "kfaturamento", "kadm"),
            entry("faturamento/aih/fechar-competencia", "kfaturamento", "aihauditar", "kadm"),
            entry("faturamento/aih/fechar", "kfaturamento", "aihauditar", "kadm"),
            entry("faturamento/aih/reabrir", "kfaturamento", "aihauditar", "kadm"),
            entry("faturamento/aih/cancelar", "kfaturamento", "aihauditar", "kadm"),
            entry("faturamento/aih", "kfaturamento", "kadm"),
            entry("faturamento/retornos/importar", "kfaturamento", "faturamentosusimportarretorno", "kadm"),
            entry("faturamento/retornos", "kfaturamento", "kadm"),

            entry("pharmacia", "kfarmaciacentral", "kfarmhmsfx", "kremedios", "pfarmaciaprodutos", "pfarmaciaestoque", "pfarmaciadispensacao", "pfarmaciapermutas", "pfarmaciaoperacoes", "pfarmaciaavaliacao", "pfarmaciarelatorios", "kadm"),

            entry("estoquecentral", "kfarmaciacentral", "kremedios", "kadm"),
            entry("zamed", "kfarmaciacentral", "kremedios", "kadm"),
            entry("visitantes", "krecepcao", "kadm"),
            entry("api/violencias", "ktriagem", "kenfermagem", "kassistencia", "kmedico", "kadm"),

            entry("assinaturas-clinicas", "krecepcao", "kmedico", "kdentista", "kenfermagem", "ktcenfermagem", "kconsultorio_medico", "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia", "pevolucaomultidisciplinar", "pverificarassinatura", "kadm"),
            entry("interoperabilidade", "kmedico", "kenfermagem", "kfaturamento", "kadm"),
            entry("prontuario", "ktriagem", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente", "kadm"),
            entry("prontuarioclinico", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente", "kadm"),
            // Equipe multidisciplinar le o historico (menu Multidisciplinar > Historico completo).
            // Seguro por ser so leitura: HistoricoPacienteController e ApsHistoricoController so tem GET.
            entry("historicopaciente", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos", "pnir", "pccih", "kadm",
                    "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia", "pevolucaomultidisciplinar"),
            // Mesmo publico de "historicopaciente" (Prontuario Completo): quem ja
            // pode ver o prontuario completo do paciente pode pedir a analise de
            // risco por IA sobre os mesmos dados.
            entry("analise-risco", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento", "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos", "pnir", "pccih", "kadm"),

            // --- Adicionadas em 2026-07-20: rotas achadas sem mapeamento na
            // auditoria completa (94+ controllers cruzados contra este catalogo),
            // antes de ligar ghbam.security.rbac.enforce. Sem esses 15 pontos,
            // ligar o enforcement bloquearia essas rotas pra TODO MUNDO (o
            // filtro nega por padrao quando a rota nao tem mapeamento nenhum).
            entry("api/chat/mensagens", "pchatprofissionais", "kadm"),
            entry("api/pacs/proxy", "ppacs", "kadm"),
            entry("api/rag", "pragprontuario", "kmedico", "kenfermagem", "ktriagem", "kadm"),

            entry("anotacaoenfermagem", "kenfermagem", "panotacoesenfermagem", "kadm"),
            entry("backend_balanco_hidrico_completo", "kenfermagem", "kmedico", "pbalancohidrico", "kadm"),
            entry("configuracao-conformidade-sla", "kadm"),
            entry("conformidade-pendencia", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento", "pauditoriaclinica", "kadm"),
            entry("drive", "pdriveenfermagem", "pcaddrives", "kenfermagem", "kadm"),
            // Alias da rota frontend /base2/drives/:menu para manter o caminho dinamico coberto.
            entry("drives", "pdriveenfermagem", "pcaddrives", "kenfermagem", "kadm"),
            entry("imagens", "ppacs", "kadm"),
            entry("listasm3u", "kadm"),
            entry("monitoramento", "kmedico", "kenfermagem", "krh", "pmonitoramento", "kadm"),
            entry("fluxo-pacientes", "krecepcao", "ktriagem", "kmedico", "kenfermagem", "ktcenfermagem", "kfaturamento", "ppainelnir", "pfluxopacientes", "kadm"),
            entry("protocolos-fluxograma", "kmedico", "kadm", "pprotocoloaprovacao"),
            entry("rh/ponto", "krh", "prhponto", "kadm"),
            entry("rh", "krh", "pcontroleplantoes", "pprodutividaderh", "ptreinamentocertificacao", "pgerenciarcertificados", "kadm"),
            entry("unidadevideoconfig", "kadm"),
            entry("unidadevideos", "kadm"),
            // Só os GETs do kiosk (isPublicKioskRequest) ficam públicos; o resto fecha por padrão.
            entry("painelvoz", "kadm"),

            // Consulta CPF+nascimento -> CNS no cadastro publico do CNES, usada no
            // cadastro do paciente. Mesmo publico de "pacientes": quem cadastra e
            // quem fatura. Sem esta linha o filtro nega por padrao (rota sem mapeamento).
            entry("consulta-cns", "krecepcao", "ktriagem", "kmedico", "kdentista", "kenfermagem",
                    "kfaturamento", "kadm"),

            // Migracao/exportacao de dados entre instalacoes. O controller ja checa
            // kadm/pmigracaodados por dentro; a linha aqui mantem o mesmo par no
            // filtro, senao o RBAC nega a rota por falta de mapeamento.
            entry("api/migracao-dados", "pmigracaodados", "kadm"),

            // Configuracao de destino/credencial de envio automatico de sumario de
            // alta em FHIR (RISAH/RNDS-ready) e consulta/reenvio manual da fila.
            // Mais especifico que "interoperabilidade" (abaixo), entao vence no
            // matching por prefixo mais longo.
            entry("interoperabilidade/fhir/destino", "pfhirsumarioalta", "kadm"),
            entry("interoperabilidade/fhir/envios", "pfhirsumarioalta", "kfaturamento", "kadm"),
            // Resultados laboratoriais sao dados clinicos identificaveis: rota
            // protegida por perfil clinico e validacao de unidade no servico.
            entry("interoperabilidade/fhir/resultados-laboratorio", "klaboratorio", "kmedico", "kenfermagem", "kadm"));

    // Permissoes OBRIGATORIAS (logica E) por rota, em cima do resultado (OU) de
    // routePermissions acima. So entra aqui rota de uso EXCLUSIVO daquela
    // permissao fina (nenhum outro papel/setor acessa a mesma rota por outro
    // motivo) - confirmado por auditoria completa das ~145 permissoes finas em
    // 2026-09-17 (ver [[project_ghbam_rbac_gap_permissoes_finas]] na memoria do
    // projeto). Ainda SEM efeito em producao: so passa a bloquear quando
    // ghbam.security.rbac.enforceRequiredPermissions=true (default false, modo
    // shadow - grep RBAC-SHADOW-REQUIRED nos logs antes de ligar). kadm tem
    // bypass proprio no RbacRequestFilter, entao nao precisa das flags aqui.
    //
    // Excluidos de proposito desta lista (rota compartilhada com permissao de
    // setor mais ampla, ou multiplas permissoes finas irmas onde exigir uma so
    // quebraria quem entra pela outra): a maioria das ~145 permissoes,
    // incluindo pimprimirbam (nenhuma rota bam* e' exclusiva dela - ver acima).
    // pcadleitos (localcenso) e plistausuarios (servidor) tambem ficaram de fora
    // por ora: a rota-mae tem sub-rotas proprias que precisariam ser neutralizadas
    // uma a uma pra nao herdar a exigencia por cascata de prefixo mais longo.
    private final Map<String, List<String>> routeRequiredAllPermissions = Map.ofEntries(
            entry("salas", "pcadsalas"),
            entry("sala", "pcadsalas"),
            entry("setores", "pcadsetores"),
            entry("unidades", "pcadunidades"),
            entry("configuracaounidade/atualizar", "pcadunidades"),
            entry("configuracaounidade/remover", "pcadunidades"),
            entry("integracao-sus", "pintegracaosus"),
            entry("pacs", "ppacs"),
            entry("imagens", "ppacs"),
            entry("api/pacs/proxy", "ppacs"),
            entry("pacientes/exportar", "pexportardadoslgpd"),
            entry("lgpd", "pexportardadoslgpd"),
            entry("modeloprescricaoenf", "pmodeloprescricaoenf"),
            entry("api/chat/mensagens", "pchatprofissionais"),
            entry("interoperabilidade/fhir/destino", "pfhirsumarioalta"));

    private static Map.Entry<String, List<String>> entry(String pathPrefix, String... permissions) {
        return Map.entry(normalize(pathPrefix), List.of(permissions));
    }

    public Optional<List<String>> requiredPermissionsFor(String requestPath) {
        String normalized = normalize(requestPath);
        return routePermissions.entrySet().stream()
                .filter(item -> normalized.equals(item.getKey()) || normalized.startsWith(item.getKey() + "/"))
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    // Lista de permissoes que devem valer com logica E (todas obrigatorias),
    // adicional ao requiredPermissionsFor (OU). Lista vazia = sem exigencia
    // extra para a rota. Mesmo casamento por prefixo mais longo do metodo acima.
    public List<String> requiredAllPermissionsFor(String requestPath) {
        String normalized = normalize(requestPath);
        return routeRequiredAllPermissions.entrySet().stream()
                .filter(item -> normalized.equals(item.getKey()) || normalized.startsWith(item.getKey() + "/"))
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(List.of());
    }

    public Map<String, List<String>> all() {
        return routePermissions.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String normalize(String value) {
        return String.valueOf(value == null ? "" : value)
                .replaceAll("^/+", "")
                .replaceAll("/+$", "")
                .toLowerCase(Locale.ROOT);
    }
}
