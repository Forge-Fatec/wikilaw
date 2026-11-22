# Tema claro e escuro — SCRUM-254

O botão **Modo escuro / Modo claro** fica no login e no cabeçalho da aplicação.
A troca é imediata e preserva o formulário, a pesquisa e a sessão atuais.
No celular, o botão do cabeçalho mostra apenas o ícone; o nome acessível continua
indicando a ação, e o controle aceita teclado e tem área mínima de 44 × 44 px.
O espaçamento do cabeçalho no desktop mantém sua altura anterior para que abrir
os filtros não introduza rolagem em telas de 1366 × 768.

## Preferência e aplicação

- Sem escolha salva, o tema acompanha `prefers-color-scheme`, inclusive mudanças
  feitas no sistema enquanto a página está aberta.
- A escolha explícita tem prioridade e é salva em `localStorage`, na chave
  `wikilaw:theme`. Ela vale para esse navegador, atravessa o login e é restaurada
  ao recarregar. A preferência não é associada à conta no servidor.
- Valores ausentes ou inválidos usam o tema do sistema. Mudanças de armazenamento
  em outra aba são sincronizadas; remover a chave volta a usar o sistema.
- Se o navegador bloquear o armazenamento, a alternância continua funcionando,
  mas a escolha só dura até a página ser recarregada.
- Um script no `index.html` aplica `data-theme` antes de carregar a aplicação.
  `src/theme.ts` controla a preferência e os eventos de sistema/armazenamento;
  `ThemeToggle` observa o estado com `useSyncExternalStore` e remove seus listeners
  quando é desmontado.

As cores de fundo, superfícies, texto, bordas, destaques, erros e avisos usam
variáveis CSS em `src/index.css`. O tema cobre login, formulários, cartões,
dashboard, análises com IA, anexos e diálogos. Os tooltips dos gráficos usam as
mesmas variáveis. O cabeçalho e o hero mantêm a identidade escura nos dois modos.

## Testes: métodos, cenários e resultados

Arquivo: [`frontend/tests/scrum-254-theme.spec.ts`](../frontend/tests/scrum-254-theme.spec.ts).

Os três primeiros cenários são testes unitários da função pura `resolveTheme`:
não abrem uma página nem usam uma API. Os oito seguintes são testes E2E da
interface com Playwright e respostas HTTP simuladas. Os mesmos cenários rodam
nos projetos Chromium, Firefox e WebKit.

### Testes unitários

| Teste | Método testado | Verificação | Resultado inicial |
| --- | --- | --- | --- |
| `preserva a escolha clara mesmo com sistema escuro` | `resolveTheme('light', true)` | A preferência explícita retorna `light`. | Aprovado nos 3 projetos |
| `preserva a escolha escura mesmo com sistema claro` | `resolveTheme('dark', false)` | A preferência explícita retorna `dark`. | Aprovado nos 3 projetos |
| `usa o sistema quando a preferência está ausente ou inválida` | `resolveTheme()` | Para `null`, `undefined`, string vazia, valor inválido e número, retorna `dark` ou `light` conforme o sistema. | Aprovado nos 3 projetos |

### Testes E2E

| Teste | Métodos/componentes exercitados | Verificação | Resultado inicial |
| --- | --- | --- | --- |
| `acompanha a preferência do sistema enquanto não há escolha salva` | Inicialização em `index.html`, `getTheme()`, `subscribeTheme()`, `ThemeToggle` | Inicia escuro, apresenta `aria-pressed=true` e `color-scheme: dark`, e muda para claro com o sistema. | Aprovado nos 3 navegadores |
| `alterna pelo teclado e mantém a escolha no login, na aplicação e após recarregar` | `setTheme()`, `getTheme()`, `subscribeTheme()`, `ThemeToggle` | Enter alterna o tema; a chave é salva; login e recarga preservam a escolha; alternar não apaga o caso digitado; o sistema não substitui a escolha explícita. | Aprovado nos 3 navegadores |
| `a escolha salva tem prioridade sobre o sistema desde a abertura` | Inicialização, `resolveTheme()`, leitura de preferência | Preferência clara salva prevalece sobre sistema escuro e aplica a cor clara do login. | Aprovado nos 3 navegadores |
| `ignora uma preferência salva inválida` | Inicialização, `resolveTheme()`, leitura de preferência | Um valor inválido não impede a abertura e usa o sistema escuro. | Aprovado nos 3 navegadores |
| `continua alternando quando o armazenamento está bloqueado` | `setTheme()`, leitura de preferência, `ThemeToggle` | Simula `SecurityError`; alterna nos dois sentidos sem erro JavaScript e mantém a escolha na página durante mudanças do sistema. | Aprovado nos 3 navegadores |
| `sincroniza a escolha entre abas e volta ao sistema quando ela é removida` | Listener de `storage` em `subscribeTheme()` | Uma segunda aba grava `light`; a primeira atualiza. Ao remover a chave, a primeira volta ao sistema escuro. | Aprovado nos 3 navegadores |
| `mantém o controle acessível no celular sem rolagem horizontal` | `ThemeToggle`, estilos responsivos | Em 390 × 844, o botão está visível no login e no cabeçalho, alterna o tema e não produz rolagem horizontal. Gera capturas para revisão. | Aprovado nos 3 navegadores |
| `aplica as cores aos resultados, dashboard e diálogo de exportação` | Variáveis CSS, `ThemeToggle`, resultados e `ExportPdfDialog` | Pesquisa com API simulada; confere fundo e texto escuros dos resultados, fundo dos indicadores e diálogo; volta ao fundo claro. Gera capturas dos dois temas. | Aprovado nos 3 navegadores |

### Execução inicial

Em 22/11/2026, na imagem `mcr.microsoft.com/playwright:v1.62.1-noble`, a mesma do CI:

| Verificação | Resultado |
| --- | --- |
| `npm run lint` | Aprovado, sem erros |
| `npm run build` | Aprovado |
| Playwright, somente `scrum-254-theme.spec.ts` | **33 aprovados, 0 falhas, 0 ignorados (32,9 s)**: 3 testes unitários + 8 E2E, executados em cada um dos 3 projetos |
| Tema e regressão de filtros (`scrum-254-theme.spec.ts` + `hero-filters.spec.ts`) | **54 aprovados, 0 falhas, 0 ignorados (57,0 s)** nos 3 navegadores; inclui ausência de rolagem com os filtros abertos em 1366 × 768 e 390 × 844 |
| Revisão visual | Capturas de login no celular e resultados no desktop, nos temas claro/escuro, inspecionadas |

As capturas e relatórios ficam em `frontend/test-results/` e não são versionados.
Os testes de interface usam APIs simuladas; não exigem backend nem chave de IA.

## Como executar

Com Node e as dependências/navegadores do Playwright instalados, na pasta `frontend`:

```sh
npm ci
npm run lint
npm run build
npx playwright test tests/scrum-254-theme.spec.ts
```

Para a suíte completa, use `npx playwright test`. Para um único navegador, acrescente
`--project=chromium`, `--project=firefox` ou `--project=webkit`.

Manualmente: entre no site, alterne o tema no login, faça login, realize uma
pesquisa, abra um diálogo e alterne novamente pelo cabeçalho. Recarregue para
confirmar a persistência. Sem escolha salva, altere a preferência do sistema
e confira que a interface acompanha a mudança.
