# Dashboard da pesquisa

## Visão geral

O dashboard resume as decisões judiciais (jurisprudência) que entram no recorte
da pesquisa ativa. Os cálculos são feitos no backend, a partir do mesmo filtro
usado por `GET /api/documentos/decisoes`, para que os números acompanhem a
busca.

Só jurisprudência entra nas agregações: em precedentes o campo `decisao` traz a
tese firmada, não o resultado do recurso; doutrina não tem decisão.

## Critérios de aceitação — SCRUM-193

- **CA01 — Indicadores principais:** DADO que a pesquisa possui decisões, QUANDO o
  usuário visualizar o dashboard, ENTÃO verá a aderência (%) e o total de
  decisões analisadas no recorte atual.
- **CA02 — Distribuição do mérito:** DADO que há decisões com resultado
  classificado, QUANDO o usuário visualizar o dashboard, ENTÃO verá um gráfico
  de barras com a quantidade por resultado do recurso: provido, parcialmente
  provido, desprovido e não identificado, conforme os dados disponíveis.
- **CA03 — Distribuição por Câmaras/Turmas:** DADO que há decisões com órgão
  julgador informado, QUANDO o usuário visualizar o dashboard, ENTÃO verá um
  gráfico de barras com a quantidade por Câmara ou Turma.
- **CA04 — Distribuição temporal:** DADO que há decisões julgadas em diferentes
  anos, QUANDO o usuário visualizar o dashboard, ENTÃO verá um gráfico de barras
  com a quantidade de decisões por ano de julgamento. Decisões sem data aparecem
  na categoria `Sem data` quando existirem.
- **CA05 — Pesquisa ativa:** DADO que o usuário pesquisou um termo ou aplicou
  filtros, QUANDO visualizar o dashboard, ENTÃO os indicadores e gráficos
  refletirão o recorte ativo da pesquisa.

## Endpoints

| Endpoint | Retorno |
| --- | --- |
| `GET /api/dashboard` | Resumo completo, com todas as agregações abaixo |
| `GET /api/dashboard/indicadores` | Aderência e totais |
| `GET /api/dashboard/distribuicao-merito` | Decisões por resultado do recurso |
| `GET /api/dashboard/distribuicao-camara-turma` | Decisões por órgão julgador |
| `GET /api/dashboard/distribuicao-tribunal` | Decisões por tribunal |
| `GET /api/dashboard/evolucao-anual` | Decisões por ano de julgamento |

Use o resumo quando a tela exibe várias agregações: ele consulta o banco uma
vez só. Os endpoints separados servem a quem precisa de uma agregação isolada.

## Filtros

Todos os endpoints aceitam os mesmos parâmetros opcionais da busca:

| Parâmetro | Formato | Observação |
| --- | --- | --- |
| `termo` | texto, até 300 caracteres | Mesma busca por palavras-chave da jurisprudência |
| `fonte` | sigla, até 30 caracteres | Não diferencia maiúsculas |
| `tribunal` | sigla, até 20 caracteres | Não diferencia maiúsculas |
| `dataDe` | `AAAA-MM-DD` | Data de julgamento inicial |
| `dataAte` | `AAAA-MM-DD` | Data de julgamento final |

Respostas de erro:

- `dataDe` posterior a `dataAte` retorna `400`;
- data fora do formato ou texto acima do limite retorna `400` com o campo em
  `details`.

Fonte ou tribunal sem cadastro **não** é erro: o recorte fica vazio. A tela monta
essas opções a partir de todas as categorias, então pode oferecer uma fonte que
só tem precedentes (como o STJ).

## Formato das respostas

```json
{
  "indicadores": {
    "totalDecisoes": 49,
    "classificadas": 26,
    "favoraveis": 10,
    "aderencia": 38
  },
  "distribuicaoMerito": [
    { "categoria": "Provido", "quantidade": 3 },
    { "categoria": "Parcialmente provido", "quantidade": 7 },
    { "categoria": "Desprovido", "quantidade": 16 },
    { "categoria": "Não identificado", "quantidade": 23 }
  ],
  "distribuicaoCamaraTurma": [
    { "categoria": "Presidência do Tribunal", "quantidade": 8 },
    { "categoria": "Outros", "quantidade": 16 }
  ],
  "distribuicaoTribunal": [
    { "categoria": "TJDFT", "quantidade": 49 }
  ],
  "evolucaoAnual": [
    { "categoria": "2025", "quantidade": 12 },
    { "categoria": "2026", "quantidade": 37 }
  ]
}
```

Os endpoints separados devolvem só o trecho correspondente: um objeto de
indicadores ou uma lista de `{ categoria, quantidade }`.

## Regras de cálculo

- **Resultado do recurso:** classificado pelo texto da decisão em Provido,
  Parcialmente provido, Desprovido ou Não identificado
  (`ResultadoDecisao`). Expressões negativas são verificadas primeiro, porque
  "desprovido" e "improcedente" contêm "provido" e "procedente".
- **Aderência:** favoráveis (provido + parcialmente provido) sobre as decisões
  classificadas, de 0 a 100. É `null` quando nenhuma decisão foi classificada.
- **Distribuição de mérito:** sempre as quatro categorias, na mesma ordem,
  inclusive com quantidade zero.
- **Órgão julgador e tribunal:** ordenados por quantidade (decrescente) e nome.
  No máximo 8 categorias; acima disso, as menores são somadas em `Outros`.
  Nome vazio vira `Não informado`, e espaços repetidos são normalizados.
- **Evolução anual:** anos em ordem cronológica; decisões sem data de
  julgamento ficam em `Sem data`, no fim.
- O filtro de período usa a **data de julgamento**. A listagem da tela ainda
  filtra no navegador pela data de publicação, então os números podem divergir
  quando há período aplicado.

## Exemplos

```powershell
$base = 'http://localhost:8080'
Invoke-RestMethod "$base/api/dashboard?termo=dano%20moral"
Invoke-RestMethod "$base/api/dashboard/distribuicao-tribunal?dataDe=2025-01-01"
Invoke-RestMethod "$base/api/dashboard/evolucao-anual?fonte=TJDFT"
```
