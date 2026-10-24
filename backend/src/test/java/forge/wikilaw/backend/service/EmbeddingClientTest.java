package forge.wikilaw.backend.service;

import com.sun.net.httpserver.HttpServer;
import forge.wikilaw.backend.service.semantic.EmbeddingClient;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

class EmbeddingClientTest {
    HttpServer server;
    final ObjectMapper json=new ObjectMapper();
    @AfterEach void close() { if(server!=null) server.stop(0); }

    EmbeddingClient server(String model,int dimensions,boolean zero,AtomicInteger calls) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/embed",exchange->{
            var request=json.readTree(exchange.getRequestBody().readAllBytes());
            calls.incrementAndGet();
            var vectors=new ArrayList<float[]>();
            for (var ignored:request.get("texts")) {
                float[] vector=new float[dimensions]; if(!zero) vector[0]=1; vectors.add(vector);
            }
            byte[] body=json.writeValueAsBytes(Map.of("model",model,"dimensions",dimensions,"vectors",vectors));
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        return new EmbeddingClient(json,"http://127.0.0.1:"+server.getAddress().getPort());
    }
    @Test void validatesRealHttpContractAndSplitsWorkIntoBoundedBatches() throws Exception {
        var calls=new AtomicInteger();
        var client=server(EmbeddingClient.MODEL,384,false,calls);
        assertThat(client.embed(Collections.nCopies(17,"Contexto jurídico completo"),"query")).hasSize(17);
        assertThat(calls.get()).isEqualTo(3);
        assertThat(client.embed(List.of(),"passage")).isEmpty();
        assertThat(calls.get()).isEqualTo(3);
    }
    @Test void incompatibleModelIsNotSilentlyStoredOrCompared() throws Exception {
        var client=server("another-model",384,false,new AtomicInteger());
        assertThatThrownBy(()->client.embed(List.of("caso"),"query")).hasMessageContaining("503");
    }
    @Test void incompatibleDimensionsAreRejected() throws Exception {
        var client=server(EmbeddingClient.MODEL,3,false,new AtomicInteger());
        assertThatThrownBy(()->client.embed(List.of("caso"),"query")).hasMessageContaining("503");
    }
    @Test void zeroVectorsCannotEnterCosineSearch() throws Exception {
        var client=server(EmbeddingClient.MODEL,384,true,new AtomicInteger());
        assertThatThrownBy(()->client.embed(List.of("caso"),"query")).hasMessageContaining("503");
    }
}
