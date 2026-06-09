package br.com.atom.api_chronus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de resposta para tratamentos de ponto.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TratamentoPontoResponseDTO {

    /** ID do tratamento */
    private Long id;

    /** PIS com 12 dígitos */
    private String pis;

    /** Nome do funcionário */
    private String nomeFuncionario;

    /** Data no formato dd/MM/yyyy */
    private String data;

    /** Horário no formato HH:mm */
    private String horario;

    /**
     * Ocorrência:
     *   I = Incluido
     *   D = Desconsiderado
     *   P = Pre-assinalacao
     */
    private String ocorrencia;

    /** Descrição legível da ocorrência */
    private String ocorrenciaDescricao;

    /** Motivo do tratamento */
    private String motivo;

    /** Nome do documento comprovante (null se não anexado) */
    private String documentoNome;

    /**
     * URL para download do documento.
     * Null se não houver documento anexado.
     * Exemplo: "/api/tratamentos/15/documento"
     */
    private String documentoUrl;

    /** Tipo MIME do documento */
    private String documentoTipo;

    /** Data e hora do registro no formato dd/MM/yyyy HH:mm */
    private String criadoEm;
}