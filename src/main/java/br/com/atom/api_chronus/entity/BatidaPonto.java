package br.com.atom.api_chronus.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidade que representa uma batida de ponto do AFD.
 *
 * Mapeada para a tabela "batidas_ponto" no PostgreSQL.
 * O campo NSR é único — garante que não há duplicatas
 * mesmo que a sincronização rode várias vezes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "batidas_ponto", indexes = {
        @Index(name = "idx_batida_pis",      columnList = "pis"),
        @Index(name = "idx_batida_datetime", columnList = "date_time"),
        @Index(name = "idx_batida_nsr",      columnList = "nsr", unique = true)
})
public class BatidaPonto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * NSR — Número Sequencial de Registro.
     * Único por registro no relógio — usado para sincronização incremental.
     */
    @Column(nullable = false, unique = true)
    private Long nsr;

    /** PIS do funcionário com 12 dígitos e zeros à esquerda */
    @Column(nullable = false, length = 12)
    private String pis;

    /** Data e hora da batida */
    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    /**
     * Tipo de batida: 1=Entrada, 2=Saída, 3=Entrada intervalo, 4=Saída intervalo
     * 0 = não classificado (relógio não informou o tipo)
     */
    @Column(nullable = false)
    private Integer tipo;

    /** Descrição legível do tipo de batida */
    @Column(name = "tipo_descricao", length = 30)
    private String tipoDescricao;

    /** Linha original do AFD para rastreabilidade e auditoria */
    @Column(name = "linha_original", length = 50)
    private String linhaOriginal;

    /** Momento em que o registro foi inserido no banco */
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

   /**
     * Indica se o comprovante por e-mail já foi enviado.
     * Evita reenvio em sincronizações subsequentes.
     */
    @Column(name = "email_enviado", nullable = false)
    private Boolean emailEnviado = false; 
}