package br.com.atom.api_chronus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Representa uma linha parseada do arquivo AFD do iDClass.
 *
 * Layout posicional fixo — Portaria 671/INMETRO:
 *
 * Posição  Tamanho  Conteúdo
 * ──────────────────────────────────────────────────────
 *  0-8       9      NSR  — Número Sequencial de Registro
 *  9-20     12      Data e hora — DDMMAAAAHHmm
 * 21-22      2      Tipo — 01=Entrada 02=Saída (outros=sem tipo)
 * 23-34     12      PIS/CPF do funcionário
 * 35-42      8      CRC/Hash de verificação
 *
 * Linhas especiais (tipo 6) têm layout diferente — são ignoradas.
 * Exemplo de linha real:
 *   000018531 3050320261802 01 295259216 2f410
 *   (sem espaços — mostrado assim só para clareza)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AfdLineDTO {

     /** NSR — identificador único e sequencial da batida no relógio */
     private Long nsr;

     /** Data e hora da batida */
     private LocalDateTime dateTime;
 
     /**
      * Tipo da batida:
      *   1 = Entrada
      *   2 = Saída
      *   3 = Entrada intervalo
      *   4 = Saída intervalo
      *  -1 = Não identificado (linhas especiais)
      */
     private Integer tipo;
 
     /** Descrição legível do tipo */
     private String tipoDescricao;
 
     /** PIS ou CPF do funcionário (12 dígitos com zeros à esquerda) */
     private String pis;
 
     /** Linha original do AFD para rastreabilidade */
     private String linhaOriginal;
}
