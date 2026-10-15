package forge.wikilaw.backend.dto;

import java.util.List;

public record DashboardResponse(
        Indicadores indicadores,
        List<DistribuicaoItem> distribuicaoMerito,
        List<DistribuicaoItem> distribuicaoCamaraTurma,
        List<DistribuicaoItem> distribuicaoTribunal,
        List<DistribuicaoItem> evolucaoAnual) {

    /** aderencia vai de 0 a 100; null quando nenhuma decisão tem resultado identificável. */
    public record Indicadores(
            long totalDecisoes,
            long classificadas,
            long favoraveis,
            Integer aderencia) {
    }

    public record DistribuicaoItem(String categoria, long quantidade) {
    }
}
