# Modelagem de dados

## Objetivo e escopo

Este documento descreve o modelo persistente identificado no código-fonte do
NutriIFTM. Ele serve como base para desenhar o modelo conceitual e o diagrama
entidade-relacionamento (DER). A seção de recomendações destaca decisões que
precisam ser avaliadas antes de transformar o modelo atual em um modelo futuro.

> **Importante:** o diagrama deve representar primeiro o que existe. Não
> desenhe cooperativa, fabricante e usuário como tipos de uma mesma conta: são
> conceitos diferentes no sistema atual.

## Visão geral do domínio

O fluxo principal de dados é:

```text
Cooperativa -> Fabricante -> Produto -> Tabela nutricional
                                         |             |
                                         |             +-> Nutrientes da tabela
                                         +-> Unidade de medida
                                         +-> Usuário criador (opcional)

Usuário -> outros usuários (seguir)
Receita -> ingredientes da receita
```

Uma cooperativa pode reunir fabricantes. Um fabricante pode estar associado a
uma cooperativa e pode ter produtos. Um produto pode ter tabelas nutricionais.
Cada tabela descreve valores nutricionais e sua porção/unidade, e pode guardar
o usuário que a criou. O vínculo do usuário à tabela não é um vínculo de
propriedade da cooperativa ou do fabricante.

## Entidades persistentes

Os nomes abaixo correspondem às classes JPA e tabelas mapeadas. Os atributos
estão resumidos para apoiar a modelagem; consulte as entidades para nomes,
tipos e detalhes completos.

| Entidade Java | Tabela | Identificador | Dados principais |
|---|---|---|---|
| `Cooperativa` | `cooperativa` | `cooCodigo` | Nome, CNPJ, endereço e cidade |
| `Fabricante` | `fabricante` | `fabCodigo` | Nome, endereço, contato e cooperativa |
| `Produto` | `produto` | `proCodigo` | Nome, nome fantasia, datas, peso, recomendações, ingredientes e fabricante |
| `TabelaNutricional` | `tabelanutricional` | `tabCodigo` | Produto, unidade, usuário criador opcional, porção, energia e dados de embalagem |
| `UnidadeMedida` | `unidademedida` | `undCodigo` | Nome da unidade |
| `Elemento` | `elemento` | `eleCodigo` | Nome, ordem de exibição e valor diário recomendado |
| `TabNutElemento` | `tab_nut_elemento` | `tneCodigo` | Valor do nutriente por porção, valor padrão e %VD |
| `Usuario` | `usuario` | `usu_codigo` | UID do Firebase, e-mail, nome, foto, localização, datas e indicador de administrador |
| `Receita` | `receita` | `id` | Nome, peso total, porção e unidade |
| `IngredienteReceita` | `ingrediente_receita` | `id` | Nome, quantidade em gramas, código TACO/TBCA e receita |

### Atributos e decisões relevantes

- `Usuario.firebaseUid` é único e representa o identificador no Firebase
  Authentication. A senha não é armazenada na tabela local.
- `Usuario.admin` é um indicador booleano. Não existe no modelo atual uma
  entidade de papéis/perfis de empresa nem categorias de conta
  `COOPERATIVA`, `FABRICANTE` ou `USUARIO`.
- `TabelaNutricional.usuario` pode ser nulo. O código documenta essa condição
  para preservar rótulos antigos sem proprietário conhecido.
- `IngredienteReceita.itemTACO` é marcado como `@Transient`: não é uma relação
  persistida no banco. Os dados nutricionais TACO/TBCA são carregados de
  arquivos JSON e resolvidos durante cálculos.
- `AdminDashboardStats`, `ChartEntry`, `ItemTACO`, `ValoresNutricionais`,
  `IngredienteConsultaDTO`, `NutrienteConsultaDTO` e `SugestaoIngrediente`
  são objetos de apoio/transferência; não os represente como tabelas JPA sem
  evidência adicional.

## Relacionamentos e cardinalidades atuais

As cardinalidades abaixo refletem os mapeamentos JPA e as colunas vistas no
esquema legado. Onde a FK permite `NULL`, a participação do lado proprietário
é opcional.

| Relação | Cardinalidade | FK / tabela associativa | Observação |
|---|---|---|---|
| Cooperativa — Fabricante | Cooperativa `0..*`; Fabricante `0..1` cooperativa | `fabricante.cooCodigo` | `Fabricante.cooperativa` é `@ManyToOne`; o formulário de cadastro pode exigir o campo, mas o mapeamento e o esquema legado permitem ausência. |
| Fabricante — Produto | Fabricante `0..*`; Produto `0..1` fabricante | `produto.fabCodigo` | `Produto.fabricante` é `@ManyToOne`; um produto novo na calculadora exige fabricante, mas a FK está mapeada como opcional. |
| Produto — TabelaNutricional | Produto `0..*`; Tabela `0..1` produto | `tabelanutricional.proCodigo` | Uma tabela aponta para um produto; não há limite de uma tabela por produto no modelo. |
| UnidadeMedida — TabelaNutricional | Unidade `0..*`; Tabela `0..1` unidade | `tabelanutricional.undCodigo` | A unidade pertence ao cadastro de unidades reutilizáveis. |
| Usuário — TabelaNutricional | Usuário `0..*`; Tabela `0..1` usuário | `tabelanutricional.usu_codigo` | Liga o rótulo ao criador quando conhecido; é usado para listar “Meus Rótulos” e autorizar alterações. |
| TabelaNutricional — TabNutElemento | Tabela `0..*`; TabNutElemento `0..1` tabela | `tab_nut_elemento.tabCodigo` | `TabNutElemento` é a entidade associativa que guarda os valores nutricionais da tabela. |
| Elemento — TabNutElemento | Elemento `0..*`; TabNutElemento `0..1` elemento | `tab_nut_elemento.eleCodigo` | Cada linha associa um tipo de nutriente a uma tabela. Não foi identificada restrição única para o par tabela/elemento. |
| Receita — IngredienteReceita | Receita `0..*`; Ingrediente `0..1` receita | `ingrediente_receita.receita_id` | A receita tem uma coleção de ingredientes com `cascade = ALL` e `orphanRemoval = true`. |
| Usuário — Usuário | Muitos-para-muitos direcionado | `usuario_seguindo(seguidor_codigo, seguido_codigo)` | A chave primária composta impede duplicar o mesmo par; seguir não é necessariamente recíproco. |

Os valores `0..1` do lado da FK indicam que o mapeamento não declara
`optional = false`/`nullable = false`; regras de formulário ou de serviço podem
ser mais restritivas em determinados fluxos.

## Rascunho textual para o DER

Este rascunho pode ser traduzido para a notação que você escolher. `PK` indica
chave primária e `FK` chave estrangeira.

```text
COOPERATIVA (PK cooCodigo, cooNome, cooCNPJ, cooEndereco, cooCidade)
  (0..N) ---- (0..1) FABRICANTE (PK fabCodigo, FK cooCodigo NULL, fabNome, fabEndereco, fabContato)

FABRICANTE (PK fabCodigo)
  (0..N) ---- (0..1) PRODUTO (PK proCodigo, FK fabCodigo NULL, proNome,
                              proNomeFantasia, proDataFabricacao,
                              proDataVencimento, proPeso, proIngredientes,
                              proRecomendacoes)

PRODUTO (PK proCodigo)
  (0..N) ---- (0..1) TABELA_NUTRICIONAL (PK tabCodigo, FK proCodigo NULL,
                                         FK undCodigo NULL, FK usu_codigo NULL,
                                         dados de porção/energia)
UNIDADE_MEDIDA (PK undCodigo, undNome)
  (0..N) ---- (0..1) TABELA_NUTRICIONAL
USUARIO (PK usu_codigo, UQ usu_firebase_uid, usu_email, usu_nome, usu_admin, ...)
  (0..N) ---- (0..1) TABELA_NUTRICIONAL
  N ---- N USUARIO via USUARIO_SEGUINDO
        (PK/FK seguidor_codigo, PK/FK seguido_codigo)

TABELA_NUTRICIONAL (PK tabCodigo)
  (0..N) ---- (0..1) TAB_NUT_ELEMENTO (PK tneCodigo, FK tabCodigo NULL,
                                       FK eleCodigo NULL, tneValor,
                                       tneValorPadrao, tneVD)
ELEMENTO (PK eleCodigo, eleNome, eleOrdem, eleValorRecomendado)
  (0..N) ---- (0..1) TAB_NUT_ELEMENTO

RECEITA (PK id, nomeProduto, pesoTotalG, porcaoG, unidadeMedidaPorcao)
  (0..N) ---- (0..1) INGREDIENTE_RECEITA (PK id, FK receita_id NULL, nome,
                                          quantidadeG, codigoTACO)
```

Em cada linha, a cardinalidade junto a cada entidade indica quantas instâncias
da entidade do outro lado podem estar relacionadas a uma instância desta.

## Persistência e fonte do esquema

- O projeto usa Spring Data JPA e MySQL Connector. O SQL legado em
  `src/main/resources/banco/nutricional.sql` identifica o banco como
  `nutricional` e foi gerado a partir de MariaDB.
- As entidades JPA definem os mapeamentos usados pela aplicação.
- A migration
  `src/main/resources/db/migration/V2__profile_and_social_users.sql` cria a
  tabela de usuários, a relação de seguidores e adiciona `usu_codigo` à tabela
  nutricional quando necessário.
- A documentação do projeto informa que essa migration pressupõe um esquema
  legado previamente importado. A migration V2, sozinha, não deve ser
  interpretada como definição completa de todas as tabelas.
- O perfil `dev` configura Hibernate para `ddl-auto=update`; em produção,
  `ddl-auto=validate` e Flyway é habilitado. Confirme o esquema real do ambiente
  antes de gerar um DER físico definitivo.

## Pontos para validar antes de fechar o modelo futuro

1. **Dono e visibilidade dos rótulos:** esclarecer se rótulos devem pertencer a
   usuários, fabricantes, cooperativas ou a mais de um desses atores. Hoje o
   dono modelado é somente `Usuario`, e pode ser nulo.
2. **Perfis de acesso:** se empresas terão login próprio, definir se uma conta
   representa uma organização, uma pessoa que trabalha para ela ou ambos.
   Considerar uma entidade de organização e uma associação usuário-organização
   com papel, em vez de converter `Fabricante` em papel de autenticação.
3. **Obrigatoriedade das FKs:** alinhar validação de formulário/serviço com
   `nullable` das colunas para cooperativa, fabricante, produto, unidade e
   usuário.
4. **Unicidade de nutrientes:** avaliar uma restrição única
   `(tabCodigo, eleCodigo)` em `tab_nut_elemento` caso cada tabela deva ter
   apenas uma linha por nutriente.
5. **Precisão numérica:** avaliar `DECIMAL` para valores de peso e nutrientes
   em vez de `double`, especialmente se forem exigidos arredondamentos
   regulatórios consistentes.
6. **Histórico e auditoria:** definir se alterações de produto/rótulo precisam
   manter versões, datas de vigência e autor da alteração.
7. **Receitas calculadas:** verificar se uma receita do domínio de receitas
   deve estar vinculada ao produto/rótulo criado pela calculadora. Essa ligação
   não aparece nos mapeamentos atuais.

## Código de referência

- Entidades: `src/main/java/com/NexGen/nutriiftm/model/`
- Repositórios: `src/main/java/com/NexGen/nutriiftm/repository/`
- Esquema legado: `src/main/resources/banco/nutricional.sql`
- Migration de usuários e social: `src/main/resources/db/migration/`
