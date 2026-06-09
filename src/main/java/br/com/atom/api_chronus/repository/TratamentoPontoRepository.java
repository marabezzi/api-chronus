package br.com.atom.api_chronus.repository;

import br.com.atom.api_chronus.entity.TratamentoPonto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositório JPA para tratamentos de ponto.
 */
@Repository
public interface TratamentoPontoRepository
        extends JpaRepository<TratamentoPonto, Long> {

    /** Lista tratamentos de um funcionário ordenados por data */
    List<TratamentoPonto> findByPisFormatadoOrderByDataAscHorarioAsc(
            String pisFormatado);

    /** Lista tratamentos de um funcionário em uma data específica */
    List<TratamentoPonto> findByPisFormatadoAndData(
            String pisFormatado, LocalDate data);

    /** Lista tratamentos de um funcionário em um período */
    List<TratamentoPonto> findByPisFormatadoAndDataBetweenOrderByDataAsc(
            String pisFormatado, LocalDate inicio, LocalDate fim);
}