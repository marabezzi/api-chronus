package br.com.atom.api_chronus.service;

import br.com.atom.api_chronus.config.AfdtEmpresaConfig;
import br.com.atom.api_chronus.entity.BatidaPonto;
import br.com.atom.api_chronus.entity.UsuarioPonto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Serviço de envio de e-mails.
 *
 * Responsável por enviar o comprovante de batida de ponto
 * ao funcionário assim que a batida é detectada na sync.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender     mailSender;
    private final AfdtEmpresaConfig  empresaConfig;

    @Value("${mail.from:Chronus Ponto <chronus@empresa.com.br>}")
    private String remetente;

    @Value("${app.timezone:America/Sao_Paulo}")
    private String timezone;

    private static final DateTimeFormatter FMT_DT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_HORA =
            DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FMT_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Envia comprovante de batida de ponto por e-mail.
     *
     * O e-mail contém apenas o horário da batida, conforme solicitado.
     * Enviado de forma assíncrona para não bloquear a sync.
     *
     * @param batida   batida de ponto recém sincronizada
     * @param usuario  funcionário com e-mail cadastrado
     */
    public void enviarComprovante(BatidaPonto batida,
                                   UsuarioPonto usuario) {
        if (usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            log.debug("Funcionario {} sem e-mail — comprovante nao enviado.",
                    usuario.getName());
            return;
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    msg, true, "UTF-8");

            helper.setFrom(remetente);
            helper.setTo(usuario.getEmail());
            helper.setSubject(gerarAssunto(batida, usuario));
            helper.setText(gerarCorpo(batida, usuario), true);

            mailSender.send(msg);
            log.info("Comprovante enviado: {} → {} | {}",
                    usuario.getName(),
                    usuario.getEmail(),
                    batida.getDateTime().format(FMT_DT));

        } catch (MessagingException e) {
            log.error("Erro ao enviar comprovante para {}: {}",
                    usuario.getEmail(), e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // MONTAGEM DO E-MAIL
    // ─────────────────────────────────────────────────────────────────────

    private String gerarAssunto(BatidaPonto batida,
                                 UsuarioPonto usuario) {
        return "Comprovante de Ponto — "
                + batida.getDateTime().format(FMT_HORA)
                + " — " + nomeAbreviado(usuario.getName());
    }

    private String gerarCorpo(BatidaPonto batida,
                               UsuarioPonto usuario) {
        String hora  = batida.getDateTime().format(FMT_HORA);
        String data  = batida.getDateTime().format(FMT_DATA);
        String agora = ZonedDateTime.now(ZoneId.of(timezone))
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width">
                  <style>
                    body { font-family: Arial, sans-serif; background:#f4f6fb;
                           margin:0; padding:20px; }
                    .card { background:#fff; max-width:480px; margin:0 auto;
                            border-radius:8px; overflow:hidden;
                            box-shadow:0 2px 8px rgba(0,0,0,.12); }
                    .header { background:#1c3966; color:#fff; padding:20px 24px; }
                    .header h1 { margin:0; font-size:16px; font-weight:bold; }
                    .header p  { margin:4px 0 0; font-size:12px;
                                 color:#adc4e8; }
                    .body { padding:28px 24px; text-align:center; }
                    .hora { font-size:56px; font-weight:bold;
                            color:#1c3966; letter-spacing:2px; margin:0; }
                    .data { font-size:14px; color:#666; margin:4px 0 20px; }
                    .nome { font-size:15px; font-weight:bold;
                            color:#333; margin:0 0 4px; }
                    .pis  { font-size:12px; color:#888; }
                    .divider { border:none; border-top:1px solid #eee;
                               margin:20px 0; }
                    .empresa { font-size:12px; color:#999; }
                    .footer { background:#f4f6fb; padding:12px 24px;
                              font-size:11px; color:#aaa;
                              text-align:center; }
                    .badge { display:inline-block; background:#e8f0fe;
                             color:#1c3966; border-radius:20px;
                             padding:4px 14px; font-size:12px;
                             font-weight:bold; margin-top:8px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="header">
                      <h1>&#10003; Comprovante de Ponto</h1>
                      <p>Registro eletrônico conforme Portaria 1510/MTE</p>
                    </div>
                    <div class="body">
                      <p class="hora">%s</p>
                      <p class="data">%s</p>
                      <hr class="divider">
                      <p class="nome">%s</p>
                      <p class="pis">PIS: %s</p>
                      <div class="badge">NSR %s</div>
                      <hr class="divider">
                      <p class="empresa">%s</p>
                    </div>
                    <div class="footer">
                      Emitido em %s &nbsp;|&nbsp; Chronus REP
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                hora,
                data,
                usuario.getName(),
                usuario.getPisFormatado(),
                String.format("%09d", batida.getNsr()),
                empresaConfig.getRazaoSocial(),
                agora
        );
    }

    /** Abrevia nome: "DONATA APARECIDA MARTINS GARCIA" → "DONATA G." */
    private String nomeAbreviado(String nome) {
        if (nome == null) return "";
        String[] partes = nome.trim().split("\\s+");
        if (partes.length == 1) return partes[0];
        return partes[0] + " "
                + partes[partes.length - 1].charAt(0) + ".";
    }
}