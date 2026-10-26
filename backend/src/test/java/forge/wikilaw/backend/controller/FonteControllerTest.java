package forge.wikilaw.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import forge.wikilaw.backend.entity.FonteDados;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class FonteControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean FonteDadosRepository fontes;

    @Test
    void listsSearchableSourcesWithSiglaAndName() throws Exception {
        when(fontes.findByAtivoTrueAndTipoFonteNotOrderByNomeAsc("PROCESSUAL"))
                .thenReturn(List.of(fonte("BDJUR", "Biblioteca Digital Jurídica do STJ"),
                        fonte("TJDFT", "Jurisprudência TJDFT")));

        mvc.perform(get("/api/fontes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sigla").value("BDJUR"))
                .andExpect(jsonPath("$[0].nome").value("Biblioteca Digital Jurídica do STJ"))
                .andExpect(jsonPath("$[1].sigla").value("TJDFT"));
    }

    private static FonteDados fonte(String sigla, String nome) {
        FonteDados fonte = new FonteDados();
        fonte.setSigla(sigla);
        fonte.setNome(nome);
        return fonte;
    }
}
