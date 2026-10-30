# Software Architecture Document (SAD)

## 1. Propósito

Este documento descreve a arquitetura implementada atualmente no NutriIFTM,
uma aplicação web para cadastro de cooperativas, fabricantes/produtores,
produtos, elaboração e consulta de rótulos nutricionais, cálculo a partir de
ingredientes, receitas e geração de relatórios.

O texto registra o estado observado no código, não uma arquitetura futura
prescrita. Recomendações e limitações estão identificadas como tais.

## 2. Escopo e contexto do sistema

### Atores e sistemas externos

- **Visitante:** acessa páginas públicas, consultas e visualização de
  informações disponibilizadas pelo sistema.
- **Usuário autenticado:** entra via Firebase; pode manter o próprio perfil,
  seguir outros usuários e tem seus rótulos associados à conta quando são
  criados no fluxo autenticado.
- **Administrador:** usuário com `admin = true`, autorizado a acessar o painel
  `/admin/**`.
- **Firebase Authentication:** verifica credenciais e emite ID token.
- **Firebase Storage:** armazena fotos de perfil.
- **MySQL/MariaDB:** guarda os dados persistentes da aplicação.
- **Arquivos TACO/TBCA:** fornecem dados de alimentos/ingredientes para
  consulta e cálculo; são recursos locais, não tabelas do banco.

> Cooperativa e fabricante são entidades de negócio. O código atual não
> implementa esses conceitos como papéis de login nem associa diretamente uma
> conta `Usuario` a uma cooperativa ou fabricante.

## 3. Visão lógica: camadas

```text
Navegador
  | HTTP / HTML / JSON
  v
Controllers (Spring MVC)
  | chamadas de serviço e binding de entrada
  v
Services (casos de uso, cálculos e transações)
  | Spring Data JPA
  v
Repositories
  | ORM / SQL
  v
MySQL/MariaDB

Controllers e templates também usam:
  - Firebase Authentication para estabelecer sessão local
  - Firebase Storage para fotos de perfil
  - JSON TACO/TBCA para consulta e cálculo nutricional
  - Thymeleaf + recursos estáticos para a interface
```

### Responsabilidades por pacote

| Pacote/local | Responsabilidade |
|---|---|
| `controller/` | Rotas web, binding de formulários e requisições HTTP, composição do model Thymeleaf e respostas JSON/PDF |
| `service/` | Operações de domínio, acesso coordenado a repositórios, cálculos e geração de PDF |
| `repository/` | Persistência Spring Data JPA e consultas específicas |
| `model/` | Entidades persistentes JPA e objetos auxiliares |
| `domain/nutricional/` | Regras de domínio nutricional, identificadores e arredondamento |
| `security/` | Autorização de edição/remoção de rótulos |
| `config/` | Configuração Spring/Firebase, interceptadores, CSRF e dados compartilhados com templates |
| `relatorio/` | Código de relatório legado/auxiliar |
| `resources/templates/` | Páginas Thymeleaf e componentes compartilhados |
| `resources/static/` | CSS, JavaScript, imagens, fontes e ícones |
| `resources/taco/` | Dados JSON TACO/TBCA usados pelo cálculo/consulta |
| `resources/db/migration/` | Migrations Flyway presentes no repositório |

## 4. Tecnologias e implantação

- Java 17 e Spring Boot, conforme `pom.xml`.
- Spring MVC e Thymeleaf para páginas renderizadas no servidor; alguns
  endpoints retornam JSON e PDF.
- Spring Data JPA/Hibernate para persistência relacional.
- MySQL Connector e suporte Flyway para migrations.
- Firebase Admin SDK no backend e Firebase Web SDK no navegador para
  autenticação/foto de perfil.
- OpenPDF/iText estão declarados para geração de documentos; o serviço de
  tabela implementa geração de PDF.
- Maven Wrapper (`mvnw`/`mvnw.cmd`) para tarefas de build.

O datasource é configurado por propriedades e variáveis de ambiente. O perfil
`dev` é o padrão local e ativa logs SQL e `ddl-auto=update`; o perfil `prod`
usa `ddl-auto=validate` e evita expor detalhes de erro. As credenciais de
ambiente devem vir de configuração externa e não devem ser incluídas na
documentação.

## 5. Componentes e fluxos principais

### 5.1 Autenticação e sessão

1. O navegador autentica a pessoa no Firebase Authentication.
2. O navegador envia o ID token em `POST /auth/sessao`.
3. `AuthController` valida o token com Firebase Admin SDK.
4. O backend cria/atualiza o espelho local em `usuario` e cria uma sessão HTTP
   com UID, e-mail, nome e indicador administrativo.
5. `AuthInterceptor` protege as rotas configuradas. No estado atual, estão
   protegidas `/admin/**` e `/perfil/**`; `/admin/**` exige também
   `admin = true`.
6. `POST /auth/logout` invalida a sessão local.

Firebase é a fonte de autenticação; a tabela `usuario` é o cadastro local usado
para relacionamentos, preferências e autorização.

### 5.2 Perfil e ações sociais

`PerfilController` apresenta o perfil próprio ou o perfil de outro usuário
autenticado, carrega rótulos associados, seguidores e pessoas seguidas.
O próprio usuário pode alterar cidade/UF e foto; perfis podem ser seguidos e
deixados de seguir. As operações de perfil usam sessão e proteção CSRF.

As fotos ficam no Firebase Storage. O cliente aceita JPG, PNG ou WEBP até 5 MB;
as regras em `storage.rules` limitam criação/atualização/remoção ao UID
proprietário. O backend valida que a URL salva corresponde ao bucket e ao
caminho de foto daquele usuário.

### 5.3 Cadastro de produto e rótulo

- `CooperativaController`, `FabricanteController` e `ProdutoController`
  oferecem operações de listagem e manutenção de seus cadastros.
- O produto aponta para um fabricante, e o fabricante pode apontar para uma
  cooperativa.
- `TabelaNutricionalController` cria/consulta tabelas, associa produto e
  unidade, e permite visualizar, imprimir e gerar PDF.
- `TabNutElementoController` mantém os nutrientes vinculados à tabela.
- Ao criar a tabela por sessão autenticada, o fluxo salva o usuário criador
  quando consegue identificá-lo.
- `RotuloAuthorizationService` permite editar/remover rótulo ao administrador
  ou ao dono registrado. Rótulos legados com `usuario == null` não entram na
  verificação de proprietário implementada por esse serviço.

### 5.4 Calculadora nutricional

O fluxo exposto em `/calculadora` está implementado em
`CalculadoraController`: ele usa dados TACO/TBCA, agrega nutrientes de
ingredientes e registra produto/tabela/valores nutricionais. No caminho atual,
o controller coordena os serviços e repositórios diretamente, inclusive o
cálculo e a persistência das linhas de nutrientes. `ArredondamentoAnvisa` e
`NutrienteId` concentram regras/identificadores nutricionais usados nessa
composição.

Existe também `CalculadoraService`, com uma implementação de salvamento
anotada como transacional, mas não foram encontradas referências a essa classe
no restante do código. Portanto, não a represente como componente usado pelo
endpoint atual sem antes confirmar uma ligação ou refatoração. O método ativo
do controller não delega a gravação a esse serviço; não se deve presumir
atomicidade transacional nesse fluxo com base apenas na classe não utilizada.

O objeto `Receita` e seus `IngredienteReceita` também possuem persistência e
fluxo próprio. A receita montada para cálculo inclui referências transitórias a
itens TACO. Não foi identificada uma FK direta de `Receita` para
`Produto`/`TabelaNutricional`.

### 5.5 Consulta e geração de saída

- `IngredienteConsultaController` e `IngredienteConsultaService` fornecem
  pesquisa/consulta de dados de ingredientes.
- `TabelaNutricionalService` lista e carrega tabelas com dados necessários,
  aplica consultas do repositório e produz o PDF do rótulo.
- Templates Thymeleaf implementam as páginas HTML; componentes como menu e
  rodapé são reutilizados por outras páginas.

## 6. Rotas e segurança observada

As rotas estão distribuídas por controllers. Entre os principais prefixos:

| Prefixo | Responsabilidade principal |
|---|---|
| `/auth` | Criação e encerramento de sessão |
| `/perfil` | Perfil, edição, foto e ações de seguir |
| `/admin` | Dashboard administrativo |
| `/cooperativas` | Cadastro de cooperativas |
| `/produtores` | Cadastro de fabricantes/produtores |
| `/produtos` | Cadastro de produtos |
| `/tabela` | Listagem, edição, visualização, impressão e PDF de tabelas |
| `/nutrientes` | Cadastro de elementos nutricionais |
| `/nutrientes/valores` | Nutrientes associados a uma tabela |
| `/unidades` | Unidades de medida |
| `/calculadora` | Consulta e cálculo nutricional |
| `/receita` | Cadastro e resultado de receitas |
| `/ingredientes` | Consulta de alimentos/ingredientes |

**Limite importante do estado atual:** `WebConfig` aplica o interceptor de
autenticação explicitamente a `/admin/**` e `/perfil/**`. O serviço de
autorização de rótulos restringe determinadas operações de edição/remoção, mas
isso não equivale a uma política de papéis para todas as rotas. As rotas de
cadastro de cooperativas, fabricantes/produtores e produtos não aparecem nessa
lista de paths protegidos.

Ao desenhar a arquitetura atual, represente essa situação; se o requisito de
negócio for que apenas representantes autorizados alterem os cadastros,
documente-a como requisito pendente, não como comportamento implementado.

## 7. Configuração e banco de dados

- `application.properties` define configurações comuns e datasource por
  variáveis de ambiente.
- `application-dev.properties` ajusta logs e Hibernate para desenvolvimento.
- `application-prod.properties` desativa logs SQL e reduz detalhes de erro.
- `V2__profile_and_social_users.sql` adiciona o modelo de usuário/social e o
  vínculo opcional de usuário ao rótulo.
- A documentação existente indica que o esquema legado deve ser importado
  antes da aplicação dessa migration. Não há uma migration inicial completa
  para todo o domínio no diretório `db/migration`.
- O SQL de `banco/nutricional.sql` representa uma exportação legada; valide
  diferenças com o banco real de cada ambiente antes de fazer mudanças.

## 8. Qualidade e testes

Há testes focados em interceptador de autenticação, CSRF, tokens CSRF e
autorização de rótulos em `src/test/java`. Eles não substituem testes de
integração completos para todos os fluxos de persistência e permissões.

## 9. Decisões de arquitetura e questões em aberto

### Decisões observadas

- Renderização majoritariamente server-side com Thymeleaf, com endpoints JSON
  usados para interações pontuais.
- Firebase Authentication terceiriza autenticação; sessão HTTP do Spring
  protege as páginas.
- O modelo JPA representa cooperativa, fabricante e produto como entidades
  separadas.
- Dados TACO/TBCA são recursos JSON e não são sincronizados para tabelas locais
  por este desenho.
- O rótulo é o agregado que reúne produto, unidade, usuário opcional e valores
  de nutrientes.

### Questões que devem ser decididas

1. Quais atores podem criar, alterar e excluir cooperativas, fabricantes,
   produtos e rótulos?
2. Login será individual, organizacional ou ambos? Como associar usuários a
   organizações e controlar os papéis?
3. Qual deve ser a visibilidade de perfis e rótulos para visitantes sem login?
4. Rótulos legados sem dono devem continuar editáveis? Devem ser migrados ou
   atribuídos a um usuário/organização?
5. Receita deve ser ligada ao produto/tabela que originou o rótulo?
6. Como versionar rótulos e preservar histórico de alterações?
7. Qual banco/versão é a referência de produção e como automatizar a criação
   de um banco novo sem depender de importação manual do esquema legado?

## 10. Estrutura de código relacionada

- `src/main/java/com/NexGen/nutriiftm/controller/`
- `src/main/java/com/NexGen/nutriiftm/service/`
- `src/main/java/com/NexGen/nutriiftm/repository/`
- `src/main/java/com/NexGen/nutriiftm/model/`
- `src/main/java/com/NexGen/nutriiftm/config/`
- `src/main/java/com/NexGen/nutriiftm/security/`
- `src/main/java/com/NexGen/nutriiftm/domain/nutricional/`
- `src/main/resources/templates/`
- `src/main/resources/static/`
- `src/main/resources/taco/`
- `src/main/resources/db/migration/`
