package br.com.atom.api_chronus.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.atom.api_chronus.entity.AfdtGerado;

/**
 * Repositório JPA para AFDTs gerados.
 */
@Repository
public interface AfdtGeradoRepository extends JpaRepository<AfdtGerado, Long> {

    /** Busca AFDTs gerados para um período específico */
    List<AfdtGerado> findByDataInicialAndDataFinalOrderByDataGeracaoDesc(
            LocalDate dataInicial, LocalDate dataFinal);

    /** Busca os últimos N AFDTs gerados */
    List<AfdtGerado> findTop10ByOrderByDataGeracaoDesc();
}