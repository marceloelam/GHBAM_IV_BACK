package br.com.iv.service;

import java.util.List;

import br.com.iv.dto.ConteudoExportado;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.enums.InstrumentoRegistro;

// Strategy de exportacao por instrumento (secao 62 do escopo): cada instrumento
// (BPA-I, BPA-C, e futuramente APAC/RAAS/AIH) tem sua propria implementacao,
// sem switch gigante em um unico Service. FaturamentoRemessaService resolve a
// implementacao certa por instrumento() via CDI.
public interface FaturamentoExporter {

    InstrumentoRegistro instrumento();

    // false enquanto o layout oficial (posicoes/tamanhos de campo) nao estiver
    // confirmado por documentacao oficial (secao 82 do escopo - nao inventar).
    boolean layoutDisponivel();

    ConteudoExportado exportar(List<Afaturar> producoes, FaturamentoCompetencia competencia);
}
