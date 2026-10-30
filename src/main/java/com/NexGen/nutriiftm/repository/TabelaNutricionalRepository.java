package com.NexGen.nutriiftm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.NexGen.nutriiftm.model.TabelaNutricional;
import java.util.List;
import java.util.Optional;
@Repository
public interface TabelaNutricionalRepository extends JpaRepository<TabelaNutricional, Long> {
    @Query("SELECT DISTINCT t FROM TabelaNutricional t " +
           "LEFT JOIN FETCH t.produto " +
           "LEFT JOIN FETCH t.unidadeMedida " +
           "LEFT JOIN FETCH t.tneElementos tne " +
           "LEFT JOIN FETCH tne.elemento")
    List<TabelaNutricional> findAllComElementos();

    @Query("SELECT t FROM TabelaNutricional t " +
           "LEFT JOIN FETCH t.produto " +
           "LEFT JOIN FETCH t.unidadeMedida " +
           "LEFT JOIN FETCH t.usuario " +
           "LEFT JOIN FETCH t.tneElementos tne " +
           "LEFT JOIN FETCH tne.elemento " +
           "WHERE t.tabCodigo = :id")
    Optional<TabelaNutricional> findByIdComElementos(@Param("id") Long id);

    /**
     * Usada pela Calculadora para auto-preencher o rótulo ao selecionar um
     * produto existente. Ordenado do mais recente para o mais antigo — em
     * tese um produto tem uma única tabela, mas isso protege contra dados
     * legados com mais de uma.
     */
    @Query("SELECT DISTINCT t FROM TabelaNutricional t " +
           "LEFT JOIN FETCH t.unidadeMedida " +
           "LEFT JOIN FETCH t.tneElementos tne " +
           "LEFT JOIN FETCH tne.elemento " +
           "WHERE t.produto.proCodigo = :produtoId " +
           "ORDER BY t.tabCodigo DESC")
    List<TabelaNutricional> findByProdutoComElementos(@Param("produtoId") Long produtoId);

    /**
     * Rótulos criados por um usuário específico — usado na página
     * "Meus Rótulos" (/tabela), que só deve mostrar o que o usuário logado
     * criou, não os de todo mundo.
     */
    @Query("SELECT DISTINCT t FROM TabelaNutricional t " +
           "LEFT JOIN FETCH t.produto " +
           "LEFT JOIN FETCH t.unidadeMedida " +
           "LEFT JOIN FETCH t.tneElementos tne " +
           "LEFT JOIN FETCH tne.elemento " +
           "WHERE t.usuario.usuCodigo = :usuCodigo")
    List<TabelaNutricional> findByUsuarioComElementos(@Param("usuCodigo") Long usuCodigo);
}