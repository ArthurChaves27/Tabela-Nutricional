# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.6/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.6/maven-plugin/build-image.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.0.6/reference/using/devtools.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.6/reference/web/servlet.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.6/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Thymeleaf](https://docs.spring.io/spring-boot/4.0.6/reference/web/servlet.html#web.servlet.spring-mvc.template-engines)
* [Validation](https://docs.spring.io/spring-boot/4.0.6/reference/io/validation.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Accessing data with MySQL](https://spring.io/guides/gs/accessing-data-mysql/)
* [Handling Form Submission](https://spring.io/guides/gs/handling-form-submission/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)

### Perfil, seguidores e foto

O Flyway aplica `src/main/resources/db/migration/V2__profile_and_social_users.sql`
no banco `nutricional`. Essa migration pressupõe que o esquema legado, incluindo
a tabela `tabelanutricional`, já foi importado.

Para habilitar o envio de fotos:

1. Ative o Firebase Storage no mesmo projeto do Firebase Authentication.
2. Defina `FIREBASE_STORAGE_BUCKET` com o nome do bucket do app Web Firebase.
3. Publique as regras do arquivo `storage.rules` nas regras do Firebase Storage.

As regras permitem que cada conta envie e remova apenas suas próprias fotos de
perfil (JPG, PNG ou WEBP, até 5 MB). O navegador remove a foto anterior depois
que a nova URL é salva no perfil. As fotos continuam publicamente acessíveis
por URL de download do Firebase; senhas e arquivos de imagem não são armazenados
no MySQL.

Os perfis no sistema exigem login, mas uma URL de download do Firebase Storage
que já tenha sido compartilhada continua podendo ser acessada por quem a possui.

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.
