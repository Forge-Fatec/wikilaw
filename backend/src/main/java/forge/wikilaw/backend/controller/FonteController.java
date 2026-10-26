package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.FonteResponse;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fontes que a tela pode usar como filtro da pesquisa. Fonte processual
 * (DataJud) só alimenta processos, nunca a listagem de documentos, então fica de fora.
 */
@RestController
@RequestMapping("/api/fontes")
public class FonteController {

    static final String TIPO_PROCESSUAL = "PROCESSUAL";

    private final FonteDadosRepository fontes;

    public FonteController(FonteDadosRepository fontes) {
        this.fontes = fontes;
    }

    @GetMapping
    public List<FonteResponse> listar() {
        return fontes.findByAtivoTrueAndTipoFonteNotOrderByNomeAsc(TIPO_PROCESSUAL).stream()
                .map(fonte -> new FonteResponse(fonte.getSigla(), fonte.getNome()))
                .toList();
    }
}
