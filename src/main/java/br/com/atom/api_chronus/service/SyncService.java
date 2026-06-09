package br.com.atom.api_chronus.service;

import br.com.atom.api_chronus.dto.AfdLineDTO;
import br.com.atom.api_chronus.dto.AfdResponseDTO;
import br.com.atom.api_chronus.dto.UsuarioDTO;
import br.com.atom.api_chronus.entity.BatidaPonto;
import br.com.atom.api_chronus.entity.LogSincronizacao;
import br.com.atom.api_chronus.entity.UsuarioPonto;
import br.com.atom.api_chronus.repository.BatidaPontoRepository;
import br.com.atom.api_chronus.repository.LogSincronizacaoRepository;
import br.com.atom.api_chronus.repository.UsuarioPontoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Serviço de sincronização entre o relógio iDClass e o banco PostgreSQL.
 *
 * Agendamentos:
 *   → A cada 1 min: verifica se é hora de sincronizar batidas
 *     (intervalo configurável em /api/config → sync.intervalo.minutos)
 *   → A cada 1 min: verifica se é hora de enviar resumo WhatsApp
 *     (hora configurável em /api/config → whatsapp.resumo.hora)
 *   → Meia-noite: sincronização completa (usuários + batidas)
 *
 * Notificações por nova batida:
 *   → E-mail (se email.habilitado=true e funcionário tem e-mail)
 *   → WhatsApp CADA_BATIDA (se whatsapp.habilitado=true e preferência)
 *
 * Notificações de resumo:
 *   → WhatsApp RESUMO_DIA no horário configurado
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final PunchLogService            punchLogService;
    private final UsuarioService             usuarioService;
    private final EmailService               emailService;
    private final WhatsAppService            whatsAppService;
    private final ConfiguracaoService        configService;
    private final BatidaPontoRepository      batidaRepo;
    private final UsuarioPontoRepository     usuarioRepo;
    private final LogSincronizacaoRepository logRepo;

    /** Controle do intervalo de sync (evita runs desnecessários) */
    private LocalDateTime ultimaSyncBatidas = null;

    /** Controle para não enviar resumo duas vezes no mesmo horário */
    private int ultimoResumoHora = -1;

    // ─────────────────────────────────────────────────────────────────────
    // AGENDAMENTOS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Verifica a cada 1 minuto se é hora de sincronizar batidas.
     * O intervalo real é lido de /api/config → sync.intervalo.minutos
     * Padrão: 5 minutos. Não bloqueia se sync estiver desabilitado.
     */
    @Scheduled(fixedDelay = 60_000)
    public void verificarSincronizacaoAutomatica() {
        if (!configService.isSyncHabilitado()) return;

        int intervaloMin = configService.getSyncIntervalo();

        if (ultimaSyncBatidas == null
                || ChronoUnit.MINUTES.between(
                        ultimaSyncBatidas,
                        LocalDateTime.now()) >= intervaloMin) {

            log.info("Sincronizacao automatica (intervalo: {} min)",
                    intervaloMin);
            sincronizarBatidas();
            ultimaSyncBatidas = LocalDateTime.now();
        }
    }

    /**
     * Verifica a cada 1 minuto se é hora de enviar o resumo diário.
     * Horário configurável em /api/config → whatsapp.resumo.hora
     * Padrão: 18h. Envia apenas uma vez por hora.
     */
    @Scheduled(fixedDelay = 60_000)
    public void verificarResumoDiario() {
        if (!configService.isWhatsappHabilitado()) return;

        int horaConfigurada = configService.getWhatsappResumoHora();
        ZonedDateTime agora = ZonedDateTime.now(
                ZoneId.of(configService.getTimezone()));

        int horaAtual   = agora.getHour();
        int minutoAtual = agora.getMinute();

        // Dispara apenas no primeiro minuto da hora configurada
        if (horaAtual == horaConfigurada
                && minutoAtual == 0
                && ultimoResumoHora != horaAtual) {

            log.info("Enviando resumo diario WhatsApp ({}h)...",
                    horaConfigurada);
            enviarResumosDiarios();
            ultimoResumoHora = horaAtual;
        }

        // Reset do controle à meia-noite
        if (horaAtual == 0) {
            ultimoResumoHora = -1;
        }
    }

    /**
     * Sincronização completa diária à meia-noite.
     * Sincroniza usuários + batidas.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void sincronizacaoCompletaDiaria() {
        log.info("=== Sincronizacao completa diaria (meia-noite) ===");
        sincronizarCompleto();
        ultimaSyncBatidas = LocalDateTime.now();
    }

    // ─────────────────────────────────────────────────────────────────────
    // MÉTODOS PÚBLICOS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Sincronização completa: usuários + batidas.
     * Chamado pelo controller e pelo agendamento diário.
     */
    @Transactional
    public LogSincronizacao sincronizarCompleto() {
        LogSincronizacao ls = iniciarLog("COMPLETA");
        int total = 0;
        try {
            total += sincronizarUsuariosInterno();
            total += sincronizarBatidasInterno(ls);
            ultimaSyncBatidas = LocalDateTime.now();
            return finalizarLog(ls, "SUCESSO", total, null);
        } catch (Exception e) {
            log.error("Erro na sincronizacao completa: {}",
                    e.getMessage(), e);
            return finalizarLog(ls, "ERRO", total, e.getMessage());
        }
    }

    /**
     * Sincroniza somente batidas (incremental por NSR).
     * Chamado pelo controller e pelo agendamento automático.
     */
    @Transactional
    public LogSincronizacao sincronizarBatidas() {
        LogSincronizacao ls = iniciarLog("AFD");
        try {
            int total = sincronizarBatidasInterno(ls);
            ultimaSyncBatidas = LocalDateTime.now();
            return finalizarLog(ls, "SUCESSO", total, null);
        } catch (Exception e) {
            log.error("Erro ao sincronizar batidas: {}",
                    e.getMessage(), e);
            return finalizarLog(ls, "ERRO", 0, e.getMessage());
        }
    }

    /**
     * Sincroniza somente usuários.
     * Chamado pelo controller.
     */
    @Transactional
    public LogSincronizacao sincronizarUsuarios() {
        LogSincronizacao ls = iniciarLog("USUARIOS");
        try {
            int total = sincronizarUsuariosInterno();
            return finalizarLog(ls, "SUCESSO", total, null);
        } catch (Exception e) {
            log.error("Erro ao sincronizar usuarios: {}",
                    e.getMessage(), e);
            return finalizarLog(ls, "ERRO", 0, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // IMPLEMENTAÇÕES INTERNAS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Sincroniza usuários do relógio → banco.
     * Insere novos e atualiza campos do relógio nos existentes.
     * Preserva campos locais (cargo, setor, email, whatsapp, etc.).
     */
    private int sincronizarUsuariosInterno() {
        log.info("Sincronizando usuarios...");
        List<UsuarioDTO> usuarios = usuarioService.listarTodos();

        if (usuarios.isEmpty()) {
            log.warn("Nenhum usuario retornado pelo relogio.");
            return 0;
        }

        int count = 0;
        for (UsuarioDTO dto : usuarios) {
            Optional<UsuarioPonto> existente =
                    usuarioRepo.findByPis(dto.getPis());
            UsuarioPonto entity = existente.orElse(new UsuarioPonto());

            // Campos do relógio
            entity.setPis(dto.getPis());
            entity.setPisFormatado(dto.getPisFormatado());
            entity.setName(dto.getName());
            entity.setCode(dto.getCode());
            entity.setTemplatesCount(dto.getTemplates_count());
            entity.setAdmin(dto.getAdmin());
            entity.setRfid(dto.getRfid());
            entity.setRegistration(dto.getRegistration());
            entity.setUltimaSincronizacao(LocalDateTime.now());

            // Campos locais — inicializa apenas se novo registro
            if (existente.isEmpty()) {
                entity.setAtivo(true);
                entity.setSupervisor(false);
                entity.setWhatsappHabilitado(false);
                entity.setWhatsappPreferencia("CADA_BATIDA");
            }

            usuarioRepo.save(entity);
            count++;
        }

        log.info("Usuarios sincronizados: {}", count);
        return count;
    }

    /**
     * Sincroniza batidas de forma incremental.
     * Busca apenas NSR maior que o último salvo no banco.
     * Para cada nova batida: envia e-mail e/ou WhatsApp CADA_BATIDA.
     */
    private int sincronizarBatidasInterno(LogSincronizacao ls) {
        Long ultimoNsr = batidaRepo.findMaxNsr().orElse(0L);
        log.info("Ultimo NSR no banco: {}", ultimoNsr);

        AfdResponseDTO afd =
                punchLogService.buscarBatidas(ultimoNsr + 1);

        if (afd == null || afd.getBatidas() == null
                || afd.getBatidas().isEmpty()) {
            log.info("Nenhuma batida nova.");
            ls.setUltimoNsr(ultimoNsr);
            return 0;
        }

        int count   = 0;
        long maxNsr = ultimoNsr;

        for (AfdLineDTO dto : afd.getBatidas()) {
            if (!batidaRepo.existsByNsr(dto.getNsr())) {

                BatidaPonto entity = new BatidaPonto();
                entity.setNsr(dto.getNsr());
                entity.setPis(dto.getPis());
                entity.setDateTime(dto.getDateTime());
                entity.setTipo(dto.getTipo());
                entity.setTipoDescricao(dto.getTipoDescricao());
                entity.setLinhaOriginal(dto.getLinhaOriginal());
                entity.setCriadoEm(LocalDateTime.now());
                entity.setEmailEnviado(false);

                batidaRepo.save(entity);
                count++;

                if (dto.getNsr() > maxNsr) maxNsr = dto.getNsr();

                // Notifica o funcionário
                notificarFuncionario(entity);
            }
        }

        ls.setUltimoNsr(maxNsr);
        log.info("Batidas novas sincronizadas: {} | Maior NSR: {}",
                count, maxNsr);
        return count;
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOTIFICAÇÕES
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Notifica o funcionário sobre a nova batida.
     * Tenta e-mail e/ou WhatsApp CADA_BATIDA conforme configuração.
     * Marca email_enviado=true independente do resultado.
     */
    private void notificarFuncionario(BatidaPonto batida) {
        try {
            boolean emailOn   = configService.isEmailHabilitado();
            boolean whatsOn   = configService.isWhatsappHabilitado();

            if (!emailOn && !whatsOn) {
                batida.setEmailEnviado(true);
                batidaRepo.save(batida);
                return;
            }

            usuarioRepo.findByPisFormatado(batida.getPis())
                    .ifPresentOrElse(
                        usuario -> {
                            // E-mail
                            if (emailOn) {
                                try {
                                    emailService.enviarComprovante(
                                            batida, usuario);
                                } catch (Exception e) {
                                    log.warn("Falha e-mail NSR {}: {}",
                                            batida.getNsr(),
                                            e.getMessage());
                                }
                            }

                            // WhatsApp — somente CADA_BATIDA
                            if (whatsOn && Boolean.TRUE.equals(
                                    usuario.getWhatsappHabilitado())
                                    && "CADA_BATIDA".equals(
                                            usuario.getWhatsappPreferencia())) {
                                try {
                                    whatsAppService
                                            .enviarComprovanteBatida(
                                                    batida, usuario);
                                } catch (Exception e) {
                                    log.warn("Falha WhatsApp NSR {}: {}",
                                            batida.getNsr(),
                                            e.getMessage());
                                }
                            }

                            batida.setEmailEnviado(true);
                            batidaRepo.save(batida);
                        },
                        () -> {
                            log.debug("PIS {} sem cadastro local.",
                                    batida.getPis());
                            batida.setEmailEnviado(true);
                            batidaRepo.save(batida);
                        }
                    );

        } catch (Exception e) {
            log.warn("Erro ao notificar NSR {}: {}",
                    batida.getNsr(), e.getMessage());
        }
    }

    /**
     * Envia resumo diário via WhatsApp para todos os funcionários
     * com preferência RESUMO_DIA e WhatsApp habilitado.
     * Chamado automaticamente no horário configurado.
     */
    private void enviarResumosDiarios() {
        ZoneId zone = ZoneId.of(configService.getTimezone());
        LocalDate hoje = LocalDate.now(zone);
        LocalDateTime inicioDia = hoje.atStartOfDay();
        LocalDateTime fimDia    = hoje.atTime(23, 59, 59);

        List<UsuarioPonto> funcionarios =
                usuarioRepo.findByAtivoTrueOrderByNameAsc();

        int enviados = 0;
        for (UsuarioPonto usuario : funcionarios) {
            if (!Boolean.TRUE.equals(usuario.getWhatsappHabilitado()))
                continue;
            if (!"RESUMO_DIA".equals(
                    usuario.getWhatsappPreferencia()))
                continue;
            if (usuario.getWhatsappNumero() == null
                    || usuario.getWhatsappNumero().isBlank())
                continue;

            List<BatidaPonto> batidosHoje =
                    batidaRepo.findByPisAndDateTimeBetweenOrderByDateTimeAsc(
                            usuario.getPisFormatado(),
                            inicioDia, fimDia);

            if (!batidosHoje.isEmpty()) {
                try {
                    whatsAppService.enviarResumoDiario(
                            usuario, batidosHoje);
                    enviados++;
                } catch (Exception e) {
                    log.warn("Falha resumo WhatsApp {}: {}",
                            usuario.getName(), e.getMessage());
                }
            }
        }

        log.info("Resumos diarios enviados: {}/{}", enviados,
                funcionarios.size());
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS DE LOG
    // ─────────────────────────────────────────────────────────────────────

    private LogSincronizacao iniciarLog(String tipo) {
        LogSincronizacao ls = new LogSincronizacao();
        ls.setTipo(tipo);
        ls.setStatus("EXECUTANDO");
        ls.setDataInicio(LocalDateTime.now());
        ls.setTotalRegistros(0);
        return logRepo.save(ls);
    }

    private LogSincronizacao finalizarLog(LogSincronizacao ls,
                                           String status,
                                           int total,
                                           String mensagem) {
        ls.setStatus(status);
        ls.setDataFim(LocalDateTime.now());
        ls.setTotalRegistros(total);
        ls.setMensagem(mensagem);
        return logRepo.save(ls);
    }
}