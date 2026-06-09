package br.com.atom.api_chronus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro de tratamento efetuado sobre dados originais do ponto.
 * Conforme coluna "Tratamentos efetuados sobre os dados originais"
 * do Anexo II da Portaria 1510/MTE.
 *
 * Ocorrências:
 *   I = Horário incluído
 *   D = Horário desconsiderado
 *   P = Pré-assinalação do período de repouso
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tratamentos_ponto", indexes = {
        @Index(name = "idx_trat_pis",  columnList = "pis_formatado"),
        @Index(name = "idx_trat_data", columnList = "data")
})
public class TratamentoPonto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PIS do funcionário com 12 dígitos */
    @Column(name = "pis_formatado", nullable = false, length = 12)
    private String pisFormatado;

    /** Data do tratamento */
    @Column(nullable = false)
    private LocalDate data;

    /**
     * Horário tratado no formato HH:mm.
     * Obrigatório para ocorrências I e D.
     * Não necessário para P.
     */
    @Column(length = 5)
    private String horario;

    /**
     * Tipo de ocorrência:
     *   I = Horário incluído
     *   D = Horário desconsiderado
     *   P = Pré-assinalação do período de repouso
     */
    @Column(nullable = false, length = 1)
    private String ocorrencia;

    /**
     * Motivo do tratamento.
     * Obrigatório para I e D.
     * Não necessário para P.
     * Exemplo: "Atestado médico", "Esquecimento de registro"
     */
    @Column(length = 500)
    private String motivo;

    /** Nome original do arquivo comprovante */
    @Column(name = "documento_nome", length = 255)
    private String documentoNome;

    /** Caminho do arquivo no servidor */
    @Column(name = "documento_path", length = 500)
    private String documentoPath;

    /**
     * Tipo MIME do documento.
     * Valores aceitos: application/pdf, image/jpeg, image/png
     */
    @Column(name = "documento_tipo", length = 50)
    private String documentoTipo;

    /** Data e hora do registro */
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}