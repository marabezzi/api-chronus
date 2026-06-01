package br.com.atom.api_chronus.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.atom.api_chronus.entity.LogSincronizacao;

/**
 * Repositório JPA para logs de sincronização.
 */
@Repository
public interface LogSincronizacaoRepository extends JpaRepository<LogSincronizacao, Long> {

    /** Busca os últimos logs ordenados por data */
    List<LogSincronizacao> findTop20ByOrderByDataInicioDesc();

    /** Busca o último log de sucesso do AFD para obter o NSR máximo */
    Optional<LogSincronizacao> findTopByTipoAndStatusOrderByDataInicioDesc(
            String tipo, String status);
}