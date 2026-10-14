# Exportação dos resultados em PDF (SCRUM-185)

Com resultados disponíveis, selecione **Exportar PDF** no cabeçalho da pesquisa. O botão mostra **Gerando PDF...** durante o processamento e o navegador inicia o download quando o arquivo estiver pronto. A exportação fica desabilitada enquanto a busca carrega ou quando não há resultados.

## Conteúdo

O relatório A4 inclui data e hora de geração, descrição do caso da última pesquisa executada, categorias, fonte, tribunal, período, ordenação e quantidade de resultados. Exporta **todos os resultados filtrados de todas as páginas**, na ordem exibida, sem repetir a busca. Editar a descrição sem pesquisar novamente não muda o termo do relatório.

Cada resultado contém título, categoria, fonte, tipo, processo ou tema, tribunal, órgão julgador, autores ou relator, datas, resumo ou ementa, decisão ou tese e informações bibliográficas disponíveis. Links HTTP/HTTPS da fonte original são clicáveis. Campos ausentes não são inventados. O relatório não inclui o inteiro teor nem os gráficos do dashboard.

Textos longos continuam em novas páginas, com cabeçalho e numeração. Fontes Noto Sans são incorporadas ao PDF para preservar acentos sem depender das fontes do servidor. A licença de redistribuição acompanha os arquivos em `backend/src/main/resources/fonts/OFL.txt`. Caracteres sem glifo nessa fonte são representados por sua notação Unicode, sem causar falha de geração.

## Endpoint

`POST /api/pesquisas/exportacoes/pdf`, com `Content-Type: application/json`:

```json
{
  "termo": "negativação indevida",
  "categorias": ["decisoes", "precedentes", "doutrina"],
  "fonte": null,
  "tribunal": null,
  "dataDe": null,
  "dataAte": null,
  "ordenacao": "mais-recentes",
  "resultados": [{ "categoria": "decisoes", "id": 123 }]
}
```

O navegador envia somente referências dos documentos e metadados da pesquisa. O backend busca o conteúdo canônico no banco em lotes de até 250 IDs e mantém a ordem recebida. IDs de categorias distintas podem coincidir. Documentos removidos/inativos causam `409`, pedindo atualização da pesquisa, em vez de gerar um relatório incompleto.

Resposta: `200 application/pdf`, `Content-Disposition: attachment; filename="wikilaw-pesquisa-AAAA-MM-DD.pdf"`, `Cache-Control: no-store`. O CORS expõe `Content-Disposition`. O PDF é gerado sob demanda, sem armazenamento permanente. O frontend valida o tipo de arquivo e a assinatura `%PDF-` antes de iniciar o download e libera a URL temporária em seguida.

Listas vazias, duplicatas, categorias inválidas, referências incompatíveis, período invertido e mais de 5.000 resultados recebem `400`. O frontend orienta a refinar filtros acima de 5.000 resultados. Relatórios acima de 1.000 páginas recebem `413`. Esses limites protegem a geração síncrona; nenhum resultado é truncado silenciosamente. A importação pode continuar em paralelo: o relatório usa os IDs presentes na pesquisa ao clicar, não documentos que chegaram depois.

## Critérios de aceite e validação

- CA01: o botão solicita o endpoint, que retorna um PDF válido.
- CA02: o PDF contém dados da pesquisa e conteúdo canônico de todas as categorias selecionadas, com ordem e paginação preservadas.
- CA03: o navegador baixa o arquivo após a geração; erros permitem nova tentativa.

`PesquisaPdfIntegrationTest` verifica o arquivo real, fontes incorporadas, links seguros, campos, documentos retirados, limites e resultados além de um lote. `frontend/tests/export-pdf.spec.ts` verifica download, filtros, termo efetivamente pesquisado, resultados de outras páginas, progresso, erros e nova tentativa. PDFs e imagens usados na inspeção visual ficam em `backend/target/pdf-preview/` e não são versionados.
