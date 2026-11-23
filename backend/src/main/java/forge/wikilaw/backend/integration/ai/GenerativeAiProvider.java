package forge.wikilaw.backend.integration.ai;

import tools.jackson.databind.JsonNode;

/** Modelo de linguagem que responde em JSON seguindo um schema. */
public interface GenerativeAiProvider {

    String model();

    /**
     * @param systemInstruction regras fixas do modelo
     * @param prompt conteudo da solicitacao
     * @param responseSchema schema (subconjunto OpenAPI) que a resposta deve seguir
     * @return o objeto JSON gerado pelo modelo
     */
    JsonNode generateJson(String systemInstruction, String prompt, JsonNode responseSchema);
}
