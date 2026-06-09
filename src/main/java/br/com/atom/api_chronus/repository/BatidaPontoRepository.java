package br.com.atom.api_chronus.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import br.com.atom.api_chronus.entity.BatidaPonto;

/**
 * Repositório JPA para batidas de ponto.
 * Spring Data JPA gera a implementação automaticamente.
 */
@Repository
public interface BatidaPontoRepository extends JpaRepository<BatidaPonto, Long> {

    /** Busca todas as batidas de um funcionário ordenadas por data */
    List<BatidaPonto> findByPisOrderByDateTimeAsc(String pis);

    /** Busca batidas de um funcionário em um período */
    List<BatidaPonto> findByPisAndDateTimeBetweenOrderByDateTimeAsc(
            String pis, LocalDateTime inicio, LocalDateTime fim);

    /** Retorna o maior NSR já sincronizado (para sincronização incremental) */
    @Query("SELECT MAX(b.nsr) FROM BatidaPonto b")
    Optional<Long> findMaxNsr();

    /** Verifica se um NSR já existe no banco */
    boolean existsByNsr(Long nsr);

    /** Total de batidas no banco */
    long count();

     /**
     * Busca batidas por período (sem filtro de PIS).
     * Útil para gerar AFDT de todos os funcionários em um período.
     */
    List<BatidaPonto> findByDateTimeBetweenOrderByDateTimeAsc(
        LocalDateTime inicio, LocalDateTime fim);

        /** Busca batidas novas que ainda não tiveram e-mail enviado */
        List<BatidaPonto> findByEmailEnviadoFalse();
}