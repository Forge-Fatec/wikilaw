# Mutação aplicada a orientação a objetos no WikiLaw

## Escopo e escolha

Execução local em 25/11/2026, branch `test/mutation-testing`, base `39339eb` com as mudanças descritas neste documento. O exemplo de `DescricaoPesquisa` permanece disponível no perfil padrão.

O perfil adicional `mutation-oo` analisa `forge.wikilaw.backend.entity.ProcessoAssuntoId`, uma chave composta real de persistência. Nenhum arquivo de produção foi modificado. A classe herda de `Object`, sobrescreve `equals(Object)` e `hashCode()`, e encapsula dois identificadores privados.

A chamada `Object chave = new ProcessoAssuntoId(10L, 20L); chave.equals(outra)` demonstra despacho dinâmico: o método executado é o da classe concreta. O `HashSet` permite observar esses métodos trabalhando em uma coleção. Não se trata de uma hierarquia artificial criada para a apresentação.

**Distinção de escopo:** o experimento aplica mutadores tradicionais do PIT a métodos sobrescritos. Não usa um mutador especializado que remova overrides ou altere hierarquias. Não afirmar que cobre todo o catálogo de mutação específica de OO.

## Comparação controlada

Os dois arquivos de teste foram acrescentados nesta mudança. A linha de base abaixo é didática e explícita, não representa a suíte histórica completa do projeto.

- `ProcessoAssuntoIdTest`: um teste básico que compara dois objetos com os mesmos componentes por referências `Object`.
- `ProcessoAssuntoIdMutationTest`: três testes adicionais de igualdade via `Object`, comportamento em `HashSet`, simetria, reflexividade, componentes nulos e hash consistente entre iguais.

Nenhum teste existente foi removido, desativado ou enfraquecido. Os quatro testes passam com o código original.

O PIT usa a mesma classe, os mesmos operadores padrão e o mesmo ambiente nos dois cenários. O perfil exclui getters e setters gerados por Lombok para concentrar a demonstração no comportamento de igualdade. Construtores não foram excluídos; os oito mutantes efetivamente gerados pertencem a `equals` e `hashCode`.

## Resultados reais

| Indicador | Teste básico | Contrato OO |
|---|---:|---:|
| Testes unitários focados (JUnit) | 1 | 4 |
| Mutantes gerados | 8 | 8 |
| KILLED | 3 | 7 |
| SURVIVED | 2 | 1 |
| NO_COVERAGE | 3 | 0 |
| Mutation Score | 38% (3/8) | 88% (7/8) |
| Test Strength | 60% (3/5) | 88% (7/8) |
| Cobertura de linhas da classe | 53% (8/15) | 73% (11/15) |

Valores exatos do score: 37,5% e 87,5%; o PIT exibe 38% e 88%. A variação é de 50 pontos percentuais, quatro mutantes adicionais detectados. Os números descrevem apenas a classe e a seleção de testes configuradas, não a correção do WikiLaw inteiro. As contagens internas de testes examinados/executados pelo PIT não são a contagem de métodos de teste unitário do JUnit.

### Mutante usado na apresentação

`BooleanTrueReturnValsMutator`, `equals`, linha 36: substitui o resultado booleano final por `true`.

- Antes: `SURVIVED`. Comparar `(10, 20)` com `(10, 20)` ainda dá `true` nas duas versões.
- Depois: `KILLED`. Comparar `(10, 20)` com `(10, 21)` precisa dar `false`, mas o mutante devolve `true`.

Isso pode tornar chaves distintas indevidamente iguais. O novo teste verifica cada componente separadamente e também a presença de chaves distintas na coleção. O relatório identifica o teste responsável pela detecção.

### Sobrevivente restante

`PrimitiveReturnsMutator`, `hashCode`, linha 42: substitui o retorno por zero. Isso aumenta colisões, mas um hash constante não viola por si só o contrato: objetos iguais continuam com hash igual e objetos diferentes podem compartilhar hash. O `HashSet` continua distinguindo objetos por igualdade.

Os testes não exigem uma fórmula fixa de hash nem hashes distintos para cada chave. Não foi feita medição de desempenho, portanto não afirmar equivalência de desempenho ou ausência de impacto operacional. O sobrevivente não representa automaticamente um defeito funcional. Essa é uma oportunidade de discutir equivalência relativa ao comportamento observado e custo de colisões sem perseguir 100% artificialmente.

## Como reproduzir

A partir de `backend`, com JDK 25 em `JAVA_HOME`:

```powershell
./mvnw.cmd '-Dtest=ProcessoAssuntoId*Test' test

# A: apenas o teste básico; relatório preservado em diretório separado
./mvnw.cmd '-Pmutation-oo' '-Dmutation.oo.tests=forge.wikilaw.backend.entity.ProcessoAssuntoIdTest' '-Dmutation.oo.reports=target/pit-reports-oo-before' org.pitest:pitest-maven:mutationCoverage

# B: teste básico e três testes do contrato OO
./mvnw.cmd '-Pmutation-oo' org.pitest:pitest-maven:mutationCoverage

# Validação geral
./mvnw.cmd test
```

O primeiro comando compila o código e os testes. Os argumentos `mutation.oo.tests` e `mutation.oo.reports` são propriedades explícitas do perfil, para que os cenários sejam reproduzíveis sem editar o POM ou remover testes. O perfil padrão de `DescricaoPesquisa` não muda.

Relatórios completos, regeneráveis e ignorados pelo Git:

- `target/pit-reports-oo-before/index.html` e `mutations.xml`;
- `target/pit-reports-oo/index.html` e `mutations.xml`.

Cópias dos XML usados nesta comparação estão em `mutation-evidence/oo-before.xml` e `oo-after.xml`, junto ao resumo versionável `summary.json`.

## Ambiente

- Java: JDK 25.0.4.1 já instalado localmente.
- Maven Wrapper: 3.9.16, conforme `.mvn/wrapper/maven-wrapper.properties`.
- PIT Maven: 1.30.2; adaptador Jupiter: `pitest-junit5-plugin` 1.2.3.
- Spring Boot: 4.1.1; quatro threads PIT.
- Sem banco, rede ou contexto Spring nos quatro testes focados.

## Roteiro simples para apresentar

1. Dois objetos podem representar a mesma chave mesmo sendo instâncias diferentes.
2. A classe sobrescreve os métodos de `Object` para definir essa igualdade.
3. Um teste só com objetos iguais aceita um mutante que devolve `true` indevidamente.
4. Os testes novos também comparam objetos diferentes e verificam uma coleção.
5. O mutante passa de sobrevivente para morto; quatro mutantes adicionais são detectados.
6. O hash constante sobrevive por um motivo que precisa ser interpretado, não escondido.

Pergunta: isso é mutação específica de OO? Resposta: são operadores tradicionais aplicados a métodos sobrescritos, exercitados por despacho dinâmico e coleções; não remoção de herança ou override.

Pergunta: por que não 100%? Resposta: o sobrevivente retorna hash constante, permitido pelo contrato funcional; avaliar desempenho seria outro objetivo.

Pergunta: o código original estava errado? Resposta: não; a mudança acrescenta testes e configuração de análise.

Pergunta: a linha de base era a suíte histórica? Resposta: não; é um teste básico novo, usado explicitamente para comparar cenários didáticos. A suíte existente foi preservada.

## Referências

- https://pitest.org/quickstart/basic_concepts/
- https://pitest.org/quickstart/mutators/
- https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Object.html
