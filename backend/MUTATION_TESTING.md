# Testes de mutação no WikiLaw

## 1. Introdução

Teste de mutação avalia a qualidade de uma suíte de testes por meio de pequenas alterações automáticas no bytecode do código de produção. Cada alteração é um **mutante**. Se algum teste falha, o mutante foi **morto** (`KILLED`); se todos continuam passando, ele **sobreviveu** (`SURVIVED`). Um mutante não executado por nenhum teste é classificado como `NO_COVERAGE`.

O objetivo não é alterar permanentemente a aplicação. O PITest (PIT) cria e executa as versões mutadas em processos isolados e mantém o código-fonte de produção intacto.

## 2. Objetivo no WikiLaw

Esta demonstração aplica o PIT a uma classe real do backend para mostrar que cobertura e testes aprovados não garantem, sozinhos, que os testes detectem defeitos nas fronteiras de uma regra. O exemplo compara a suíte existente com a mesma suíte acrescida de um teste de valor-limite.

## 3. Ferramentas e compatibilidade verificadas

| Tecnologia | Versão usada |
|---|---:|
| Java | 25.0.4.1 LTS |
| Maven | 3.9.11 |
| Spring Boot | 4.1.1 |
| JUnit Jupiter / Platform | 6.0.3 |
| PITest Maven Plugin | 1.30.2 |
| PITest JUnit 5 Plugin | 1.2.3 |

O artefato continua se chamando `pitest-junit5-plugin`, embora a versão efetiva do projeto seja JUnit Platform 6.0.3. A execução real confirmou a descoberta e execução dos testes Jupiter. O plugin JUnit 5 1.2.3 requer PIT 1.19.4 ou superior; PIT 1.30.2 atende ao requisito e executou corretamente com Java 25.

Referências: [PIT Maven Quickstart](https://pitest.org/quickstart/maven/) e [PITest JUnit 5 Plugin](https://github.com/pitest/pitest-junit5-plugin).

## 4. Classe selecionada

A classe escolhida foi `forge.wikilaw.backend.service.contexto.DescricaoPesquisa`.

Ela transforma os filtros de uma pesquisa ativa em campos textuais usados no contexto jurídico. A classe é adequada porque:

- pertence ao código real do WikiLaw e já possuía testes unitários;
- contém regras determinísticas, sem banco de dados, rede, relógio ou aleatoriedade;
- possui condições, retornos, chamadas e uma fronteira numérica simples;
- o comportamento mutado pode ser explicado sem conhecer toda a arquitetura do sistema;
- a análise fica restrita a uma única classe, tornando a demonstração rápida.

Nenhuma regra de negócio de `DescricaoPesquisa` foi modificada.

## 5. Testes existentes

`DescricaoPesquisaTest` possuía três testes:

1. descrição de todos os filtros aplicados;
2. omissão de filtros não preenchidos;
3. neutralização de uma sequência que tentaria fechar o bloco do prompt.

Todos passavam, mas nenhum exercitava uma consulta contextual com exatamente 1.000 caracteres, que é o limite definido por `MAX_CARACTERES_CONSULTA`.

## 6. Configuração do PIT

O `pom.xml` configura:

- apenas `DescricaoPesquisa` em `targetClasses`;
- `DescricaoPesquisa*Test` em `targetTests`, abrangendo o teste original e o teste de melhoria;
- relatórios HTML e XML;
- diretório estável `target/pit-reports`;
- quatro threads;
- `pitest-junit5-plugin` no classpath do plugin, não como dependência da aplicação.

O conjunto padrão de mutadores do PIT gerou estes tipos na classe:

| Mutador | Alteração exemplificada | Gerados antes |
|---|---|---:|
| `ConditionalsBoundaryMutator` | troca limites como `<=` por `<` | 1 |
| `NegateConditionalsMutator` | nega condições | 12 |
| `EmptyObjectReturnValsMutator` | substitui retorno de objeto por valor vazio | 12 |
| `VoidMethodCallMutator` | remove chamada de método `void` | 11 |

## 7. Cenário A — antes da melhoria

### Resultado real

Os três testes históricos passaram. O PIT produziu:

- 36 mutantes gerados;
- 30 mortos;
- 4 sobreviventes;
- 2 sem cobertura;
- cobertura de linhas da classe mutada: 35/41 (85%);
- Mutation Score exibido pelo PIT: 83%;
- Test Strength exibido pelo PIT: 88%.

O **Mutation Score** usado pelo relatório é `mortos / todos os mutantes`: `30 / 36 = 83,33%`, exibido como 83%. Mutantes `NO_COVERAGE` permanecem no denominador. Já o **Test Strength** exclui os mutantes sem cobertura: `30 / (30 + 4) = 88,24%`, exibido como 88%.

### Mutante escolhido

Na linha da condição abaixo, o `ConditionalsBoundaryMutator` trocou conceitualmente `<=` por `<`:

```java
if (valor == null || valor.length() <= MAX_CARACTERES_CONSULTA) return valor;
```

Com o mutante, uma consulta de exatamente 1.000 caracteres deixa de ser retornada intacta e entra indevidamente no ramo de truncamento. O resultado passa a conter 999 caracteres originais e uma reticência. É um defeito observável, mas os testes existentes usavam apenas consultas curtas; por isso o mutante sobreviveu.

Os outros três sobreviventes substituíam por string vazia os retornos dos métodos `modo`, `ordenacao` e `periodo`. Eles não foram o foco desta demonstração e explicam por que o score final não chega a 100%.

## 8. Cenário B — novo teste

Foi criada a classe `DescricaoPesquisaMutationTest`, com o teste `keepsContextualQueryAtTheExactCharacterLimit`. Ele constrói uma consulta com exatamente 1.000 caracteres e exige que o campo resultante preserve o texto integral.

O teste foi colocado em uma classe separada para permitir uma demonstração reproduzível: o cenário A seleciona somente a classe histórica; o cenário B usa o curinga configurado e inclui as duas classes. Nenhum teste oficial é removido, desabilitado ou enfraquecido.

Depois da melhoria:

- os quatro testes focados passaram;
- o mutante de fronteira mudou de `SURVIVED` para `KILLED`;
- 31 dos 36 mutantes foram mortos;
- restaram 3 sobreviventes e 2 sem cobertura;
- o Mutation Score subiu para 86%;
- o Test Strength subiu para 91%.

## 9. Comparação dos resultados

| Métrica | Antes | Depois |
|---|---:|---:|
| Testes unitários focados | 3 | 4 |
| Mutantes gerados | 36 | 36 |
| Mutantes mortos | 30 | 31 |
| Mutantes sobreviventes | 4 | 3 |
| Mutantes sem cobertura | 2 | 2 |
| Mutation Score | 83% (`30/36`) | 86% (`31/36`) |
| Test Strength | 88% (`30/34`) | 91% (`31/34`) |

Também foi executada a suíte completa do backend após a mudança: **464 testes, 0 falhas, 0 erros e 8 ignorados**.

## 10. Como reproduzir

Execute os comandos a partir da pasta `backend`. O parâmetro `maven.repo.local` mantém o cache da demonstração dentro de `target` e evita depender da configuração global do computador.

### Cenário A: somente os testes históricos

```powershell
mvn '-Dmaven.repo.local=target/mutation-demo-m2' '-Dtest=DescricaoPesquisaTest' test
mvn '-Dmaven.repo.local=target/mutation-demo-m2' '-Dmutation.boundary.tests=forge.wikilaw.backend.service.contexto.DescricaoPesquisaTest' org.pitest:pitest-maven:mutationCoverage
```

Opcionalmente, preserve o relatório antes de executar o cenário B:

```powershell
Copy-Item -Recurse target/pit-reports target/pit-reports-before
```

### Cenário B: testes históricos e novo teste

```powershell
mvn '-Dmaven.repo.local=target/mutation-demo-m2' '-Dtest=DescricaoPesquisa*Test' test
mvn '-Dmaven.repo.local=target/mutation-demo-m2' org.pitest:pitest-maven:mutationCoverage
```

### Suíte completa

```powershell
mvn '-Dmaven.repo.local=target/mutation-demo-m2' test
```

Em shells Bash, retire as aspas simples ao redor dos argumentos `-D` se desejar.

## 11. Relatórios

- relatório HTML atual: `target/pit-reports/index.html`;
- relatório XML atual: `target/pit-reports/mutations.xml`;
- cópia local da linha de base desta execução: `target/pit-reports-before/index.html` e `mutations.xml`.

`target` é um diretório de build ignorado pelo Git. Portanto, os relatórios são verificáveis e regeneráveis, mas não são versionados. Para abrir o HTML no Windows:

```powershell
Start-Process target/pit-reports/index.html
```

No HTML, navegue até o pacote `forge.wikilaw.backend.service.contexto`, abra `DescricaoPesquisa.java` e localize a linha do método `cortar`. Verde indica mutante morto; vermelho indica sobrevivente; ausência de cobertura aparece separadamente.

## 12. Teste de mutação e orientação a objetos

O PIT tradicional altera o comportamento observável dos métodos compilados: condições, limites, operações matemáticas, retornos e chamadas. Isso se relaciona à orientação a objetos porque objetos expõem comportamento por métodos e colaboram por chamadas; um mutante simula um método que toma uma decisão errada, devolve outro valor ou deixa de chamar um colaborador.

Esses operadores tradicionais não devem ser confundidos com operadores de mutação **especificamente orientados a objetos** descritos na literatura, como remover ou trocar uma sobrescrita, alterar uma chamada a `super`, substituir tipos polimórficos, mudar visibilidade, modificar ocultação de atributos ou trocar a inicialização de construtores. O conjunto padrão Gregor do PIT usado aqui aplica principalmente operadores tradicionais ao bytecode Java; esta demonstração está aplicada a código orientado a objetos, mas não afirma cobrir todo o catálogo acadêmico de operadores OO.

## 13. Conclusão

A demonstração mostra uma diferença mensurável causada por um único teste de fronteira. Antes, todos os testes convencionais passavam, mas uma alteração de `<=` para `<` não era percebida. Depois, o novo teste matou esse mutante, elevou o Mutation Score de 83% para 86% e reduziu os sobreviventes de 4 para 3, sem alterar o código de produção.

## 14. Demonstração adicional: igualdade de objetos

Foi acrescentado o perfil `mutation-oo` para analisar `ProcessoAssuntoId`, com herança de `Object`, sobrescrita de `equals`/`hashCode`, chamadas polimórficas e comportamento em `HashSet`. O exemplo original acima permanece disponível.

A comparação didática usa um teste básico novo e três testes adicionais: 3/8 mutantes mortos antes (38%) e 7/8 depois (88%). Ela não representa a suíte histórica completa. A suíte completa após a mudança registrou 468 testes, zero falhas, zero erros e oito ignorados.

Consulte [MUTATION_OO.md](MUTATION_OO.md) para o contrato, comandos reproduzíveis, interpretação do hash constante sobrevivente e evidências XML.
