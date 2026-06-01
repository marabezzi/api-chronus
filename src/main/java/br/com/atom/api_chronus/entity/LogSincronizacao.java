package br.com.atom.api_chronus.entity;

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
 * Entidade de log de sincronização com o relógio iDClass.
 *
 * Registra cada execução de sincronização (manual ou automática),
 * o resultado e o último NSR sincronizado para sincronização incremental.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "logs_sincronizacao", indexes = {
        @Index(name = "idx_log_inicio", columnList = "data_inicio"),
        @Index(name = "idx_log_tipo",   columnList = "tipo"),
        @Index(name = "idx_log_status", columnList = "status")
})
public class LogSincronizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tipo de sincronização:
     *   AFD       → batidas de ponto
     *   USUARIOS  → usuários do relógio
     *   COMPLETA  → AFD + usuários juntos
     */
    @Column(nullable = false, length = 20)
    private String tipo;

    /**
     * Status da sincronização:
     *   SUCESSO  → concluída sem erros
     *   ERRO     → falha completa
     *   PARCIAL  → concluída com alguns erros
     */
    @Column(nullable = false, length = 20)
    private String status;

    /** Momento de início da sincronização */
    @Column(name = "data_inicio", nullable = false)
    private LocalDateTime dataInicio;

    /** Momento de fim da sincronização */
    @Column(name = "data_fim")
    private LocalDateTime dataFim;

    /** Total de registros sincronizados nesta execução */
    @Column(name = "total_registros")
    private Integer totalRegistros;

    /**
     * Último NSR sincronizado.
     * Usado na próxima sincronização incremental do AFD:
     * a próxima sincronização busca a partir deste NSR + 1.
     */
    @Column(name = "ultimo_nsr")
    private Long ultimoNsr;

    /** Mensagem adicional (erro, observação, etc.) */
    @Column(length = 500)
    private String mensagem;
}