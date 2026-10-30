CREATE TABLE IF NOT EXISTS usuario (
    usu_codigo BIGINT NOT NULL AUTO_INCREMENT,
    usu_firebase_uid VARCHAR(128) NOT NULL,
    usu_email VARCHAR(255) NOT NULL,
    usu_nome VARCHAR(255) NULL,
    usu_foto_url VARCHAR(500) NULL,
    usu_cidade VARCHAR(100) NULL,
    usu_estado VARCHAR(2) NULL,
    usu_criado_em DATETIME NOT NULL,
    usu_ultimo_login DATETIME NULL,
    usu_admin BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (usu_codigo),
    UNIQUE KEY uk_usuario_firebase_uid (usu_firebase_uid)
) ENGINE=InnoDB;

SET @v2_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'usuario'
          AND column_name = 'usu_cidade'
    ),
    'SELECT 1',
    'ALTER TABLE usuario ADD COLUMN usu_cidade VARCHAR(100) NULL'
);
PREPARE v2_statement FROM @v2_ddl;
EXECUTE v2_statement;
DEALLOCATE PREPARE v2_statement;

SET @v2_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'usuario'
          AND column_name = 'usu_estado'
    ),
    'SELECT 1',
    'ALTER TABLE usuario ADD COLUMN usu_estado VARCHAR(2) NULL'
);
PREPARE v2_statement FROM @v2_ddl;
EXECUTE v2_statement;
DEALLOCATE PREPARE v2_statement;

CREATE TABLE IF NOT EXISTS usuario_seguindo (
    seguidor_codigo BIGINT NOT NULL,
    seguido_codigo BIGINT NOT NULL,
    PRIMARY KEY (seguidor_codigo, seguido_codigo),
    CONSTRAINT fk_usuario_seguindo_seguidor
        FOREIGN KEY (seguidor_codigo) REFERENCES usuario (usu_codigo),
    CONSTRAINT fk_usuario_seguindo_seguido
        FOREIGN KEY (seguido_codigo) REFERENCES usuario (usu_codigo)
) ENGINE=InnoDB;

SET @v2_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'tabelanutricional'
          AND column_name = 'usu_codigo'
    ),
    'SELECT 1',
    'ALTER TABLE tabelanutricional ADD COLUMN usu_codigo BIGINT NULL'
);
PREPARE v2_statement FROM @v2_ddl;
EXECUTE v2_statement;
DEALLOCATE PREPARE v2_statement;
