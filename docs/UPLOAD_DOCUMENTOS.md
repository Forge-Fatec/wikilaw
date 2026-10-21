# Upload de documentos do caso (SCRUM-219)

Na busca, selecione **Anexar documento** e escolha um PDF ou DOCX de até 10 MB. O sistema extrai o texto e mostra o nome, o formato e a quantidade de caracteres. Em **Conferir texto extraído**, é possível revisar e editar o contexto antes de pesquisar. Pode-se complementar a descrição digitada ou pesquisar somente com o documento. **Remover** retira o documento das próximas pesquisas; os resultados atuais continuam sendo os da última pesquisa executada até pesquisar novamente.

Os controles aparecem junto à busca no hero e acompanham a busca quando ela se encaixa na barra fixa. O envio permite cancelar, substituir um arquivo em processamento, corrigir falhas e reenviar o mesmo arquivo. Uma resposta atrasada de um arquivo substituído não sobrescreve o mais recente. A pesquisa fica desabilitada enquanto o arquivo está sendo processado.

## Formatos e limites

- PDF e DOCX, verificando extensão, MIME informado e conteúdo real. Extensão válida sozinha não permite enviar um arquivo disfarçado.
- Máximo de 10 MB por arquivo, 100.000 caracteres extraídos e 200 páginas para PDF. Nenhum texto é truncado silenciosamente.
- DOCX: texto de parágrafos, tabelas, cabeçalhos e rodapés via Apache POI; arquivos com macros são rejeitados. Antes da leitura, a expansão ZIP é limitada a 32 MB e 2.000 entradas.
- PDFs protegidos por senha/restrições ou sem texto extraível são recusados com mensagem específica. A extração usa PDFBox; OCR de documentos digitalizados não faz parte desta task.
- O upload não adiciona o documento ao acervo público. O arquivo é processado sob demanda, e seu texto fica somente no estado da tela até ser removido ou a página recarregar. Não há persistência em banco, `localStorage`, integração com IA externa ou registro do conteúdo em logs de aplicação. O servidor pode utilizar arquivos temporários durante a requisição, removidos pelo processamento multipart.

## Uso do contexto e busca atual

A `develop` prepara conteúdos do acervo para embeddings, mas ainda não executa busca vetorial. Esta task entrega a entrada do documento e conecta seu contexto à busca **textual** existente; não calcula similaridade semântica nem gera embeddings.

Ao pesquisar com um anexo, o frontend envia descrição e texto para preparar uma consulta. O backend normaliza o texto, descarta conectivos, identifica palavras recorrentes e combina termos da descrição com até 18 termos do documento, dentro do limite atual de 300 caracteres de consulta. A seleção considera o texto completo, inclusive trechos depois dos primeiros 300 caracteres. Os termos são usados em jurisprudência, precedentes, doutrina e no recorte do dashboard. A descrição original é preservada para exibição e exportação; o PDF registra o nome do documento usado e os termos da consulta contextual. Nenhum conteúdo integral do anexo é incorporado automaticamente ao PDF.

O endpoint de extração devolve o texto completo normalizado para que a busca por similaridade futura possa receber o mesmo contexto, sem depender dos termos da adaptação textual.

## API

`POST /api/pesquisas/documentos`, multipart, parte `arquivo`:

```json
{
  "nome": "caso.pdf",
  "formato": "PDF",
  "tamanhoBytes": 15900,
  "caracteres": 2400,
  "paginas": 3,
  "texto": "Texto extraído do documento..."
}
```

Para DOCX, `paginas` é `null`: a paginação do Word depende do layout e não é estimada pela extração.

`POST /api/pesquisas/contexto`, JSON:

```json
{ "termo": "descrição do caso", "texto": "Texto extraído ou revisado pelo usuário..." }
```

Resposta: `{ "consulta": "termos usados na busca", "termosContexto": ["termo"] }`. `termo` é opcional (até 300 caracteres); `texto` é obrigatório (até 100.000). As duas respostas usam `Cache-Control: no-store`.

Erros usam o contrato padrão `status`, `error`, `details`: `400` para JSON/campos inválidos, `413` para limites excedidos, `415` para formato incompatível e `422` para arquivo ilegível, protegido ou sem texto útil. O frontend também recusa extensão inválida, arquivo vazio e tamanho excessivo antes de enviar.

## Critérios e testes

- CA01: PDF e DOCX reais são enviados e têm o texto extraído, incluindo acentos e tabelas.
- CA02: formatos não suportados e conteúdo disfarçado são rejeitados sem processamento como documento válido.
- CA03: teste integrado demonstra que o contexto extraído encontra um documento do acervo não encontrado somente pela descrição.

`DocumentUploadIntegrationTest` cobre extração real, validação, limites, proteção/ausência de texto, expansão ZIP e busca com contexto. `SearchContextServiceTest` cobre seleção de termos, normalização e limites da consulta.
