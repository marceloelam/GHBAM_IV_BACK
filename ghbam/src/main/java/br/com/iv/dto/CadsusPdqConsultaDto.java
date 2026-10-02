package br.com.iv.dto;

// Parametros de busca do servico oficial PDQSupplierJWT (PRPA_IN201305UV02, IHE PDQ v3).
//
// Confirmado em teste real contra a homologacao (19/09/2026), usando o projeto SoapUI
// oficial do DATASUS (IHE-MS.zip, ihe-client-example-files.zip - ambos publicados em
// datasus.saude.gov.br/wp-content/uploads/2019/12/): o DATASUS RECUSA buscar so por nome
// - a regra de negocio deles exige nome + pelo menos um de {nomeMae, dataNascimento,
// municipioNascimento}. Sem isso, o erro e "Ao informar o Nome do Paciente, e obrigatorio
// informar pelo menos mais um dos seguintes dados...". dataNascimento no formato AAAAMMDD
// (ex.: "19900101"), como no exemplo oficial.
public record CadsusPdqConsultaDto(
        String cns,
        String cpf,
        String nome,
        String nomeMae,
        String dataNascimento,
        String nis) {

    public boolean possuiAoMenosUmParametro() {
        return preenchido(cns) || preenchido(cpf) || preenchido(nome) || preenchido(nomeMae) || preenchido(nis);
    }

    /** Regra de negocio real do DATASUS: nome sozinho nao e uma busca valida. */
    public boolean buscaPorNomeIncompleta() {
        return preenchido(nome) && !preenchido(nomeMae) && !preenchido(dataNascimento);
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
