package br.com.iv.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class RhDto {

    public static class ServidorSimples {
        public int id;
        public String nome;
        public String matricula;
        public String cargo;

        public ServidorSimples(int id, String nome, String matricula, String cargo) {
            this.id = id;
            this.nome = nome;
            this.matricula = matricula;
            this.cargo = cargo;
        }
    }

    public static class PlantaoRequest {
        public Integer servidorId;
        public String unidade;
        public LocalDateTime dataInicio;
        public LocalDateTime dataFim;
        public String tipo;
        public String setor;
        public String obs;
        public Integer cadastrouId;
    }

    public static class PlantaoLoteRequest {
        public List<PlantaoRequest> plantoes;
    }

    public static class PlantaoResponse {
        public int id;
        public int servidorId;
        public String servidor;
        public String matricula;
        public String unidade;
        public String dataInicio;
        public String dataFim;
        public String tipo;
        public String setor;
        public String obs;
        public String duracao;
        public boolean aberto;

        public PlantaoResponse(int id, int servidorId, String servidor, String matricula, String unidade,
                String dataInicio, String dataFim, String tipo, String setor, String obs,
                String duracao, boolean aberto) {
            this.id = id;
            this.servidorId = servidorId;
            this.servidor = servidor;
            this.matricula = matricula;
            this.unidade = unidade;
            this.dataInicio = dataInicio;
            this.dataFim = dataFim;
            this.tipo = tipo;
            this.setor = setor;
            this.obs = obs;
            this.duracao = duracao;
            this.aberto = aberto;
        }
    }

    public static class TreinamentoRequest {
        public String titulo;
        public String descricao;
        public LocalDate dataInicio;
        public LocalDate dataFim;
        public Integer cargaHoraria;
        public String categoria;
        public String unidade;
        public boolean obrigatorio;
        public Integer cadastrouId;
        public List<Integer> participantes;
    }

    public static class TreinamentoResponse {
        public int id;
        public String titulo;
        public String descricao;
        public String dataInicio;
        public String dataFim;
        public Integer cargaHoraria;
        public String categoria;
        public String unidade;
        public boolean obrigatorio;
        public int totalParticipantes;
        public int concluidos;

        public TreinamentoResponse(int id, String titulo, String descricao, String dataInicio,
                String dataFim, Integer cargaHoraria, String categoria, String unidade,
                boolean obrigatorio, int totalParticipantes, int concluidos) {
            this.id = id;
            this.titulo = titulo;
            this.descricao = descricao;
            this.dataInicio = dataInicio;
            this.dataFim = dataFim;
            this.cargaHoraria = cargaHoraria;
            this.categoria = categoria;
            this.unidade = unidade;
            this.obrigatorio = obrigatorio;
            this.totalParticipantes = totalParticipantes;
            this.concluidos = concluidos;
        }
    }

    public static class CertificacaoRequest {
        public Integer servidorId;
        public String titulo;
        public String instituicao;
        public LocalDate dataEmissao;
        public LocalDate dataValidade;
        public String arquivo;
        public String unidade;
    }

    public static class CertificacaoResponse {
        public int id;
        public String servidor;
        public String titulo;
        public String instituicao;
        public String dataEmissao;
        public String dataValidade;
        public String status;
        public String unidade;

        public CertificacaoResponse(int id, String servidor, String titulo, String instituicao,
                String dataEmissao, String dataValidade, String status, String unidade) {
            this.id = id;
            this.servidor = servidor;
            this.titulo = titulo;
            this.instituicao = instituicao;
            this.dataEmissao = dataEmissao;
            this.dataValidade = dataValidade;
            this.status = status;
            this.unidade = unidade;
        }
    }

    public static class ProdutividadeItem {
        public String servidor;
        public String matricula;
        public String cargo;
        public long totalPlantoes;
        public double totalHoras;
        public double mediaHorasPorPlantao;
        public long totalAtendimentos;
        public String tipos;
        public String setores;
        public String periodo;

        public ProdutividadeItem(String servidor, String matricula, String cargo,
                long totalPlantoes, double totalHoras, long totalAtendimentos,
                String tipos, String setores, String periodo) {
            this.servidor = servidor;
            this.matricula = matricula;
            this.cargo = cargo;
            this.totalPlantoes = totalPlantoes;
            this.totalHoras = totalHoras;
            this.mediaHorasPorPlantao = totalPlantoes > 0 ? Math.round((totalHoras / totalPlantoes) * 10.0) / 10.0 : 0;
            this.totalAtendimentos = totalAtendimentos;
            this.tipos = tipos;
            this.setores = setores;
            this.periodo = periodo;
        }
    }
}
