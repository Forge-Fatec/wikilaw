package forge.wikilaw.backend.integration.documents;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Somente hosts oficiais, sem redirecionar para URLs arbitrárias. */
@Component
public class PublicSourceHttpClient {
    private static final Set<String> HOSTS = Set.of("dadosabertos.web.stj.jus.br",
        "jurisdf.tjdft.jus.br", "bdjur.stj.jus.br", "bdtd.ibict.br", "articlemeta.scielo.org",
        "pangeabnp.pdpj.jus.br");
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private long lastScieloRequest;

    public Response request(String url, String body) {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost()) ||
                (uri.getPort() != -1 && uri.getPort() != 443) || uri.getUserInfo() != null)
            throw new IllegalArgumentException("URL externa não permitida");
        try {
            if ("articlemeta.scielo.org".equals(uri.getHost())) throttleScielo();
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json, application/hal+json, application/xml, text/csv, */*")
                .header("User-Agent", "WikiLaw-research/1.0");
            if (body == null) builder.GET();
            else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            // Respostas HTTP (inclusive 429/5xx) são auditadas antes de qualquer decisão de retry.
            var future = client.sendAsync(builder.build(), info -> new LimitedBodySubscriber());
            HttpResponse<byte[]> response;
            try {
                response = future.get(60, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                throw new IllegalStateException("Timeout de 60 segundos na resposta de " + uri.getHost(), e);
            } catch (InterruptedException e) {
                future.cancel(true);
                throw e;
            }
            return new Response(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8),
                response.headers().firstValue("Content-Type").orElse(""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Coleta interrompida", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Falha de transporte para " + uri.getHost(), e);
        }
    }
    private synchronized void throttleScielo() throws InterruptedException {
        long wait = 400 - (System.currentTimeMillis() - lastScieloRequest);
        if (wait > 0) Thread.sleep(wait);
        lastScieloRequest = System.currentTimeMillis();
    }
    public record Response(int status, String body, String contentType) {}

    private static class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private java.util.concurrent.Flow.Subscription subscription;
        private long received;
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(java.util.concurrent.Flow.Subscription s) { subscription=s; delegate.onSubscribe(s); }
        public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            for (var buffer:buffers) received+=buffer.remaining();
            if (received>MAX_BYTES) {
                subscription.cancel();
                delegate.onError(new IllegalStateException("Recurso excede 32 MiB"));
            } else delegate.onNext(buffers);
        }
        public void onError(Throwable t) { delegate.onError(t); }
        public void onComplete() { delegate.onComplete(); }
    }
}
