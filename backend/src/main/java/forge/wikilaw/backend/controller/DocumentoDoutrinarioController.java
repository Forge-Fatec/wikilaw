package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.DoutrinaResumoDTO;
import forge.wikilaw.backend.model.DocumentoDoutrinario;
import forge.wikilaw.backend.repository.DocumentoDoutrinarioRepository;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/doutrinas")
@RequiredArgsConstructor
public class DocumentoDoutrinarioController {

    private final DocumentoDoutrinarioRepository documentoDoutrinarioRepository;

    /**
     * SCRUM-129: Implementar lógica/integração de busca de doutrinas.
     *
     * GET /api/doutrinas?termo=dano moral
     * Se "termo" vier vazio, devolve todas as doutrinas cadastradas.
     *
     * Critério de aceite da SCRUM-113 (task mãe): quando não há conteúdo
     * relacionado, o endpoint simplesmente retorna uma lista vazia — quem
     * decide a mensagem "nenhum resultado encontrado" é o front, olhando
     * pra lista vazia (assim como já faz no protótipo).
     */
    @GetMapping
    public List<DoutrinaResumoDTO> buscar(@RequestParam(required = false, defaultValue = "") String termo) {
        List<DocumentoDoutrinario> doutrinas =
                documentoDoutrinarioRepository.findByTituloContainingIgnoreCaseOrResumoContainingIgnoreCase(termo, termo);

        return doutrinas.stream()
                .map(this::toDTO)
                .toList();
    }

    private DoutrinaResumoDTO toDTO(DocumentoDoutrinario doc) {
        return new DoutrinaResumoDTO(
                doc.getIdDocumento(),
                doc.getTitulo(),
                doc.getResumo(),
                doc.getDataPublicacao(),
                doc.getUrlOriginal()
        );
    }
}