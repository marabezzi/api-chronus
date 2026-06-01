package br.com.atom.api_chronus.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidade que armazena os AFDTs gerados pelo Chronus.
 *
 * Mapeada para a tabela "afdt_gerados".
 * Permite consultar histórico de arquivos gerados e reenviar sem
 * precisar regenerar do relógio.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "afdt_gerados", indexes = {
        @Index(name = "idx_afdt_geracao",  columnList = "data_geracao"),
        @Index(name = "idx_afdt_periodo",  columnList = "data_inicial, data_final"),
        @Index(name = "idx_afdt_pis",      columnList = "pis")
})
public class AfdtGerado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Momento em que o AFDT foi gerado */
    @Column(name = "data_geracao", nullable = false)
    private LocalDateTime dataGeracao;

    /** Data inicial do período do AFDT */
    @Column(name = "data_inicial", nullable = false)
    private LocalDate dataInicial;

    /** Data final do período do AFDT */
    @Column(name = "data_final", nullable = false)
    private LocalDate dataFinal;

    /**
     * PIS filtrado (null = AFDT completo com todos os funcionários).
     */
    @Column(length = 12)
    private String pis;

    /** Conteúdo completo do arquivo AFDT em texto */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String conteudo;

    /** Total de registros de detalhe (tipo 2) no arquivo */
    @Column(name = "total_registros")
    private Integer totalRegistros;
}