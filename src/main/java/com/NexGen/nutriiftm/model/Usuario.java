package com.NexGen.nutriiftm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Representa, no banco de dados local, o usuário que já existe no
 * Firebase Authentication.
 *
 * O Firebase é a fonte da verdade para autenticação (senha, provedor
 * Google, etc). Esta tabela NÃO guarda senha nenhuma — ela só espelha
 * dados básicos (uid, email, nome) para que o restante do sistema
 * possa referenciar "o usuário logado" via chave estrangeira, guardar
 * preferências, papéis/permissões, etc.
 *
 * O registro aqui é criado (ou atualizado) automaticamente pelo
 * AuthController toda vez que alguém faz login com sucesso — não
 * precisa ser inserido manualmente.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usu_codigo")
    private Long usuCodigo;

    @Column(name = "usu_firebase_uid", nullable = false, unique = true, length = 128)
    private String firebaseUid;

    @Column(name = "usu_email", nullable = false, length = 255)
    private String email;

    @Column(name = "usu_nome", length = 255)
    private String nome;

    @Column(name = "usu_foto_url", length = 500)
    private String fotoUrl;

    @Column(name = "usu_cidade", length = 100)
    private String cidade;

    @Column(name = "usu_estado", length = 2)
    private String estado;

    @Column(name = "usu_criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "usu_ultimo_login")
    private LocalDateTime ultimoLogin;

    /**
     * Papel de administrador. Contas novas podem usar o perfil, mas só
     * usuários com admin = true acessam o painel administrativo:
     *   UPDATE usuario SET usu_admin = 1 WHERE usu_email = '...';
     */
    @Column(name = "usu_admin", nullable = false)
    private boolean admin;

    @ManyToMany
    @JoinTable(
            name = "usuario_seguindo",
            joinColumns = @JoinColumn(name = "seguidor_codigo"),
            inverseJoinColumns = @JoinColumn(name = "seguido_codigo")
    )
    private Set<Usuario> seguindo = new HashSet<>();

    @ManyToMany(mappedBy = "seguindo")
    private Set<Usuario> seguidores = new HashSet<>();
}