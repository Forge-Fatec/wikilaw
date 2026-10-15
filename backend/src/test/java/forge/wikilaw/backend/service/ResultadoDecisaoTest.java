package forge.wikilaw.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ResultadoDecisaoTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Recurso provido.|PROVIDO",
        "Deram provimento ao apelo.|PROVIDO",
        "Pedido julgado procedente.|PROVIDO",
        "Recurso parcialmente provido.|PARCIAL",
        "Deram parcial provimento.|PARCIAL",
        "Procedente em parte.|PARCIAL",
        "Recurso desprovido.|DESPROVIDO",
        "Recurso improvido.|DESPROVIDO",
        "Recurso não provido.|DESPROVIDO",
        "RECURSO NÃO PROVIDO.|DESPROVIDO",
        "Negou-se provimento.|DESPROVIDO",
        "Pedido improcedente.|DESPROVIDO",
        "Homologado o acordo.|INDEFINIDO",
    })
    void classificaOResultadoAPartirDoTextoDaDecisao(String decisao, ResultadoDecisao esperado) {
        assertEquals(esperado, ResultadoDecisao.classificar(decisao));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void decisaoSemTextoEhIndefinida(String decisao) {
        assertEquals(ResultadoDecisao.INDEFINIDO, ResultadoDecisao.classificar(decisao));
    }
}
