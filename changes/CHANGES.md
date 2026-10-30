# Correções aplicadas e decisões

Este arquivo registra as correções feitas após a revisão de `erros.md`, com
seus motivos e os pontos que ainda dependem de decisão operacional.

## Segurança e acesso

- **Acesso administrativo:** a condição do `AuthInterceptor` agora agrupa a
  rota `/admin` e suas subrotas antes de verificar o papel. Isso evita bloquear
  administradores em `/admin` e continua redirecionando contas comuns. A
  comparação também remove o `contextPath` da URI, para funcionar quando a
  aplicação é hospedada sob um prefixo.
- **Perfis privados para visitantes:** conforme decisão do responsável pelo
  projeto, `/perfil` e `/perfil/{id}` agora exigem sessão. Os dados e listas do
  perfil não são apresentados a visitantes anônimos.
- **Proteção CSRF do perfil:** POSTs sob `/perfil/**` exigem token imprevisível
  associado à sessão, aceito como `_csrf` nos formulários ou `X-CSRF-TOKEN` no
  upload da foto. Ausência ou divergência resulta em HTTP 403. A proteção é
  restrita a esse conjunto de rotas; não adiciona uma nova dependência de
  segurança nem altera o comportamento das rotas públicas antigas.
- **Rotação de sessão:** após validar o token do Firebase, o backend troca o
  ID da sessão antes de registrar os atributos autenticados, reduzindo risco de
  fixação de sessão. O token CSRF antigo é removido junto com essa rotação.
- **Propriedade dos rótulos:** edição e remoção de rótulos com dono definido,
  inclusive operações sobre seus nutrientes, agora exigem o dono ou um
  administrador. O dono é carregado explicitamente para a verificação. Rótulos
  legados sem dono continuam editáveis no fluxo público existente, pois não há
  como identificar retrospectivamente seu criador. A calculadora também associa
  o usuário autenticado ao novo rótulo quando existe uma sessão.

## Dados, interface e desempenho

- **Migration MySQL:** removido o uso de `ADD COLUMN IF NOT EXISTS`, não
  portátil para todas as versões de MySQL. A migration consulta
  `information_schema` e prepara `ALTER TABLE` somente quando a coluna não
  existe, preservando a compatibilidade com esquemas parcialmente preparados.
- **Consultas do perfil:** removido o `EntityGraph` que carregava as duas
  coleções sociais em uma única consulta, evitando o produto cartesiano de
  seguidores × seguindo. As relações são carregadas dentro da transação do
  controlador. A lista “Encontrar pessoas” agora usa páginas de 20 resultados.
- **Textos e navegação:** os estados vazios distinguem perfil próprio de perfil
  alheio; a seção de atividades sem dados foi removida e a ausência de biografia
  é indicada explicitamente. Links para rotas que não existiam foram apontados
  para abas/edição existentes.
- **Método HTTP do perfil:** `GET /perfil/salvar` e `GET /perfil/foto` agora
  respondem HTTP 405, em vez de tentar converter esses nomes em um ID numérico.
- **CSS:** os limites dos dois `clamp()` da ilustração da página inicial foram
  reordenados para que o mínimo não seja maior que o máximo.
- **Mensagens de autenticação:** removidos tratamentos de HTTP 403 obsoletos
  das telas de login e cadastro.
- **Storage:** as regras agora separam criação/atualização de exclusão. Após
  salvar uma nova foto, o navegador tenta remover a foto de perfil anterior
  pertencente ao usuário. Se a remoção falhar, a interface informa o erro.

## Testes adicionados

- `AuthInterceptorTest` cobre o acesso administrativo e a negação de conta
  comum, incluindo aplicação sob `contextPath`.
- `CsrfTokenServiceTest` cobre geração, persistência por sessão, validação por
  formulário/cabeçalho e rejeição de tokens ausentes ou incorretos.
- `CsrfInterceptorTest` verifica que POST sem token é negado e que GET não é
  bloqueado pelo controle CSRF.
- `RotuloAuthorizationServiceTest` verifica dono, administrador, terceiro e
  rótulo legado sem dono.
- O wrapper `mvnw` voltou ao modo executável padrão (`755`) para uso em Linux e
  macOS.

## Limites e passos operacionais

- Publicar as novas regras de [storage.rules](./storage.rules) no Firebase
  Storage; sem isso, a remoção de fotos antigas continuará falhando e será
  reportada na interface.
- Fotos ainda usam URLs de download do Firebase com token. Exigir login na
  aplicação protege a apresentação dos perfis, mas não revoga URLs já
  compartilhadas; esse acesso direto exige uma estratégia de armazenamento
  privado/proxy se também precisar ser restringido.
- A lista de seguidores/seguindo ainda é carregada integralmente. A consulta
  deixou de juntar as duas coleções em uma só, mas paginação dessas abas pode
  ser necessária se a rede crescer muito.
- Rótulos sem dono continuam sem uma regra de propriedade, para preservar os
  fluxos legados que permitem uso sem conta. Uma política futura pode exigir
  login para criar rótulos e definir como transferir registros antigos.
- Não foi adicionada chave estrangeira ou cascata para `tabelanutricional.usu_codigo`;
  a alteração depende de validar os dados já existentes e a política de
  exclusão de usuários.
- Se a V2 já tiver sido aplicada em algum ambiente, compare o schema e o
  histórico do Flyway antes de atualizar. A alteração do arquivo muda seu
  checksum; `flyway repair` só deve ser considerado depois de confirmar que o
  schema já contém os efeitos daquela migration. Não executar `repair` às cegas.
- O diretório de trabalho não contém metadados Git, então não foi possível
  confirmar o diff histórico descrito em `erros.md`.
