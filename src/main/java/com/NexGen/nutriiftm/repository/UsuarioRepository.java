package com.NexGen.nutriiftm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.NexGen.nutriiftm.model.Usuario;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByFirebaseUid(String firebaseUid);

    Optional<Usuario> findProfileByFirebaseUid(String firebaseUid);

    @Query("SELECT u FROM Usuario u WHERE u.usuCodigo = :id")
    Optional<Usuario> findProfileById(@Param("id") Long id);

    Page<Usuario> findAllByUsuCodigoNotOrderByNomeAsc(Long usuCodigo, Pageable pageable);
}