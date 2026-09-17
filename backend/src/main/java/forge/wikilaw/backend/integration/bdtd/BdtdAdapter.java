package forge.wikilaw.backend.integration.bdtd;

import forge.wikilaw.backend.integration.documents.*;
import static forge.wikilaw.backend.integration.documents.DocumentFields.*;
import java.io.StringReader;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;

@Component
public class BdtdAdapter implements DocumentAdapter {
    private static final String OAI = "http://www.openarchives.org/OAI/2.0/";
    private static final String DC = "http://purl.org/dc/elements/1.1/";
    private final ObjectMapper json;
    public BdtdAdapter(ObjectMapper json) { this.json = json; }
    public DocumentSource source() { return DocumentSource.BDTD; }

    public SourcePage collect(DocumentImportRequest r, AuditedFetch fetch) {
        String url = "https://bdtd.ibict.br/vufind/OAI/Server?verb=ListRecords";
        if (r.resumptionToken() != null && !r.resumptionToken().isBlank())
            url += "&resumptionToken=" + enc(r.resumptionToken());
        else {
            url += "&metadataPrefix=oai_dc";
            if (r.conjunto() != null) url += "&set=" + enc(r.conjunto());
            if (r.desde() != null) url += "&from=" + enc(r.desde());
            if (r.ate() != null) url += "&until=" + enc(r.ate());
        }
        var response = fetch.get(url, "XML");
        ParsedOai parsed = parse(response.texto());
        String filter = r.query().toLowerCase(Locale.ROOT);
        var matching = parsed.records().stream().filter(n -> n.path("deleted").asBoolean(false)
            || n.toString().toLowerCase(Locale.ROOT).contains(filter)).toList();
        int from = Math.min(r.start(),matching.size());
        int to = Math.min(from+r.size(),matching.size());
        SourcePage.Continuacao next = to < matching.size()
            ? new SourcePage.Continuacao(null,to,r.resumptionToken(),null)
            : parsed.token() == null ? null : new SourcePage.Continuacao(null,0,parsed.token(),null);
        return new SourcePage(matching.subList(from,to),response.registroBrutoId(),next);
    }
    public ParsedOai parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            f.setFeature("http://xml.org/sax/features/external-general-entities",false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            var builder = f.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
                @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            var root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
            if (!"OAI-PMH".equals(root.getLocalName()) || !OAI.equals(root.getNamespaceURI()))
                throw new IllegalArgumentException("BDTD retornou página de verificação/HTML, não OAI-PMH");
            NodeList errors = root.getElementsByTagNameNS(OAI,"error");
            if (errors.getLength() > 0) {
                Element error = (Element)errors.item(0);
                if ("noRecordsMatch".equals(error.getAttribute("code"))) return new ParsedOai(List.of(),null);
                throw new IllegalArgumentException("Erro OAI-PMH: " + error.getAttribute("code"));
            }
            List<JsonNode> rows = new ArrayList<>();
            NodeList records = root.getElementsByTagNameNS(OAI,"record");
            for (int i=0;i<records.getLength();i++) {
                Element record = (Element)records.item(i);
                Element header = (Element)record.getElementsByTagNameNS(OAI,"header").item(0);
                ObjectNode row = json.createObjectNode();
                row.put("identifier",first(record,OAI,"identifier"));
                row.put("deleted",header != null && "deleted".equals(header.getAttribute("status")));
                for (String field : List.of("title","creator","contributor","description","date","type","language","subject","publisher","rights","identifier")) {
                    var values = json.createArrayNode();
                    NodeList list = record.getElementsByTagNameNS(DC,field);
                    for (int j=0;j<list.getLength();j++) values.add(list.item(j).getTextContent().trim());
                    row.set("dc_" + field,values);
                }
                rows.add(row);
            }
            String token = first(root,OAI,"resumptionToken");
            return new ParsedOai(rows,token == null || token.isBlank() ? null : token);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("BDTD: resposta não é XML OAI-PMH seguro/válido (possível verificação de navegador)",e); }
    }
    private String first(Element element,String ns,String field) {
        NodeList nodes = element.getElementsByTagNameNS(ns,field);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }
    private String values(JsonNode n,String field) {
        List<String> list = new ArrayList<>();
        for (JsonNode v : n.path("dc_"+field)) list.add(v.asText());
        return join(list);
    }
    public NormalizedDocument normalize(JsonNode n) {
        if (n.path("deleted").asBoolean(false))
            throw new UnavailableDocumentException(required(n,"identifier"),"Registro removido pela fonte OAI-PMH");
        String title = values(n,"title");
        if (title.isBlank()) throw new IllegalArgumentException("Registro OAI-PMH sem título");
        String issued = n.path("dc_date").path(0).asText("");
        String url = null;
        for (JsonNode id : n.path("dc_identifier")) {
            String candidate = id.asText();
            if (candidate.startsWith("https://") || candidate.startsWith("http://")) { url=candidate; break; }
        }
        return NormalizedDocument.builder().identificador(required(n,"identifier")).titulo(plain(title))
            .tipo(values(n,"type")).resumo(plain(values(n,"description"))).autores(values(n,"creator"))
            .palavrasChave(values(n,"subject")).idioma(values(n,"language"))
            .dataPublicacao(date(issued)).dataOriginal(issued).url(url)
            .metadados(json.writeValueAsString(n)).build();
    }
    public record ParsedOai(List<JsonNode> records,String token) {}
}
