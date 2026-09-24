package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.integration.documents.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DocumentImportService {
    private static final Logger log=LoggerFactory.getLogger(DocumentImportService.class);
    private final Map<DocumentSource,DocumentAdapter> adapters=new EnumMap<>(DocumentSource.class);
    private final CargaDadosService cargas;
    private final RegistroBrutoService raw;
    private final PublicSourceHttpClient http;
    private final ObjectMapper json;
    private final DocumentRecordProcessor processor;
    public DocumentImportService(List<DocumentAdapter> adapters,CargaDadosService cargas,RegistroBrutoService raw,
            PublicSourceHttpClient http,ObjectMapper json,DocumentRecordProcessor processor) {
        adapters.forEach(a -> this.adapters.put(a.source(),a));
        this.cargas=cargas; this.raw=raw; this.http=http; this.json=json; this.processor=processor;
    }
    public ImportResult importar(DocumentSource source,DocumentImportRequest request) {
        validate(source,request);
        CargaDados carga=cargas.iniciar(source.sigla());
        log.info("Iniciando carga documental id={} fonte={}",carga.getId(),source);
        int received=0,processed=0,errors=0;
        List<Long> ids=new ArrayList<>();
        List<String> messages=new ArrayList<>();
        List<String> warnings=new ArrayList<>();
        boolean fatal=false;
        SourcePage.Continuacao next=null;
        AtomicInteger responses=new AtomicInteger();
        try {
            SourcePage page=adapters.get(source).collect(request,(url,body,format) ->
                fetch(carga,url,body,format,responses));
            received=page.registros().size();
            warnings.addAll(page.avisos());
            next=page.proxima();
            for (var row:page.registros()) {
                try {
                    var doc=adapters.get(source).normalize(row);
                    ids.add(processor.process(source,carga.getFonte().getId(),page.registroBrutoId(),doc));
                    processed++;
                } catch (RuntimeException e) {
                    errors++;
                    if (e instanceof UnavailableDocumentException unavailable)
                        processor.indisponibilizar(source,carga.getFonte().getId(),unavailable.identificador());
                    if (messages.size()<10) messages.add("Registro "+(processed+errors)+": "+safeMessage(e));
                }
            }
        } catch (RuntimeException e) {
            fatal=true;
            errors++;
            messages.add(safeMessage(e));
        }
        String message=messages.isEmpty()?null:String.join(" | ",messages);
        CargaDados completed=cargas.concluir(carga.getId(),received,processed,errors,message,fatal);
        log.info("Carga documental id={} fonte={} status={} recebidos={} processados={} erros={}",
            carga.getId(),source,completed.getStatus(),received,processed,errors);
        return new ImportResult(carga.getId(),source,completed.getStatus(),received,processed,errors,message,ids,next,warnings);
    }
    private AuditedFetch.Payload fetch(CargaDados carga,String url,String body,String format,AtomicInteger count) {
        for (int attempt=0;attempt<3;attempt++) {
            var response=http.request(url,body);
            String actualFormat=response.contentType().toLowerCase(Locale.ROOT).contains("text/html")
                || response.body().stripLeading().toLowerCase(Locale.ROOT).startsWith("<!doctype html") ? "HTML" : format;
            var registro=raw.salvarTextoOriginal(carga,
                carga.getFonte().getSigla()+":CARGA:"+carga.getId()+":RESPOSTA:"+count.incrementAndGet(),
                response.body(),actualFormat);
            if (response.status()<200 || response.status()>=300) {
                if (attempt<2 && Set.of(502,503,504).contains(response.status())) {
                    log.warn("HTTP {} carga={} tentativa={}",response.status(),carga.getId(),attempt+1);
                    try { Thread.sleep(500L*(attempt+1)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Coleta interrompida"); }
                    continue;
                }
                throw new IllegalStateException("HTTP "+response.status()+" em "+java.net.URI.create(url).getHost()
                    +"; resposta preservada no registro bruto "+registro.getId());
            }
            if ("HTML".equals(actualFormat)) throw new IllegalStateException(
                "Fonte retornou HTML/verificação de navegador em vez de "+format+"; registro bruto "+registro.getId());
            if ("JSON".equals(format)) {
                try { json.readTree(response.body()); }
                catch (RuntimeException e) { throw new IllegalArgumentException("JSON inválido; registro bruto "+registro.getId()); }
                raw.confirmarJsonValido(registro.getId(),response.body());
            }
            return new AuditedFetch.Payload(response.body(),registro.getId());
        }
        throw new IllegalStateException("Tentativas esgotadas");
    }
    private String safeMessage(Exception e) {
        String s=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        return s.substring(0,Math.min(s.length(),500));
    }
    private void validate(DocumentSource s,DocumentImportRequest r) {
        boolean search=s==DocumentSource.BDJUR || s==DocumentSource.TJDFT || s==DocumentSource.BDTD || s==DocumentSource.PANGEA;
        boolean offset=s==DocumentSource.STJ || s==DocumentSource.STJ_PRECEDENTES || s==DocumentSource.SCIELO || s==DocumentSource.BDTD;
        if ((!search && r.termo()!=null) || (offset && r.pagina()!=null) || (!offset && r.offset()!=null)
                || (s!=DocumentSource.STJ && r.dataset()!=null)
                || (s!=DocumentSource.STJ && s!=DocumentSource.STJ_PRECEDENTES && r.recursoId()!=null)
                || (s!=DocumentSource.SCIELO && r.issn()!=null)
                || (s!=DocumentSource.BDTD && (r.conjunto()!=null || r.resumptionToken()!=null))
                || (s!=DocumentSource.SCIELO && s!=DocumentSource.BDTD && (r.desde()!=null || r.ate()!=null)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Parâmetro não suportado pela fonte; consulte a documentação");
        try {
            if (r.desde()!=null) java.time.LocalDate.parse(r.desde());
            if (r.ate()!=null) java.time.LocalDate.parse(r.ate());
        } catch (java.time.DateTimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Data inválida"); }
        if (r.desde()!=null && r.ate()!=null && r.desde().compareTo(r.ate())>0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"desde deve ser anterior a ate");
        if (s==DocumentSource.BDTD && r.resumptionToken()!=null && (r.desde()!=null || r.ate()!=null || r.conjunto()!=null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Com resumptionToken não repita desde/ate/conjunto");
    }
    public record ImportResult(Long idCarga,DocumentSource fonte,CargaStatus status,int recebidos,int processados,
        int erros,String mensagemErro,List<Long> ids,SourcePage.Continuacao proxima,List<String> avisos) {}
}
