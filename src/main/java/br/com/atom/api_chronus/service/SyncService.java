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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Serviço de sincronização entre o relógio iDClass e o banco PostgreSQL.
 *
 * Sincronizações:
 *   → A cada 1 minuto: verifica se é hora de sincronizar batidas,
 *     baseado no intervalo configurado em "sync.intervalo.minutos"
 *     (padrão: 5 minutos, configurável via /api/config)
 *   → Meia-noite: sincronização completa (batidas + usuários)
 *   → Manual: via /api/sync/completo, /api/sync/batidas, /api/sync/usuarios
 *
 * E-mail:
 *   → A cada nova batida sincronizada, envia comprovante ao funcionário
 *     se o e-mail estiver cadastrado e o envio habilitado em /api/config
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final PunchLogService            punchLogService;
    private final UsuarioService             usuarioService;
    private final EmailService               emailService;
    private final ConfiguracaoService        configService;
    private final BatidaPontoRepository      batidaRepo;
    private final UsuarioPontoRepository     usuarioRepo;
    private final LogSincronizacaoRepository logRepo;

    /** Momento da última sincronização de batidas */
    private LocalDateTime ultimaSyncBatidas = null;

    // ─────────────────────────────────────────────────────────────────────
    // AGENDAMENTOS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Verifica a cada 1 minuto se é hora de sincronizar.
     * O intervalo real é lido da configuração "sync.intervalo.minutos".
     * Padrão: 5 minutos. Configurável em /api/config.
     */
    @Scheduled(fixedDelay = 60_000)
    public void verificarSincronizacaoAutomatica() {
        if (!configService.isSyncHabilitado()) {
            return;
        }

        int intervaloMin = configService.getSyncIntervalo();

        if (ultimaSyncBatidas == null
                || ChronoUnit.MINUTES.between(
                        ultimaSyncBatidas,
                        LocalDateTime.now()) >= intervaloMin) {

            log.info("Iniciando sincronizacao automatica " +
                    "(intervalo: {} min)", intervaloMin);
            sincronizarBatidas();
            ultimaSyncBatidas = LocalDateTime.now();
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
    // MÉTODOS PÚBLICOS (chamados pelo controller e agendamentos)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Sincronização completa: usuários + batidas.
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
     * Sincroniza somente batidas (incremental).
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
     * Atualiza existentes e insere novos.
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

            entity.setPis(dto.getPis());
            entity.setPisFormatado(dto.getPisFormatado());
            entity.setName(dto.getName());
            entity.setCode(dto.getCode());
            entity.setTemplatesCount(dto.getTemplates_count());
            entity.setAdmin(dto.getAdmin());
            entity.setRfid(dto.getRfid());
            entity.setRegistration(dto.getRegistration());
            entity.setUltimaSincronizacao(LocalDateTime.now());

            // Preserva campos locais se já existir
            if (existente.isEmpty()) {
                entity.setAtivo(true);
                entity.setSupervisor(false);
            }

            usuarioRepo.save(entity);
            count++;
        }

        log.info("Usuarios sincronizados: {}", count);
        return count;
    }

    /**
     * Sincroniza batidas de forma incremental.
     * Busca apenas NSR maior que o último salvo.
     * Para cada nova batida, envia e-mail se habilitado.
     */
    private int sincronizarBatidasInterno(LogSincronizacao ls) {
        Long ultimoNsr = batidaRepo.findMaxNsr().orElse(0L);
        log.info("Sincronizando batidas. Ultimo NSR no banco: {}",
                ultimoNsr);

        AfdResponseDTO afd =
                punchLogService.buscarBatidas(ultimoNsr + 1);

        if (afd == null || afd.getBatidas() == null
                || afd.getBatidas().isEmpty()) {
            log.info("Nenhuma batida nova desde NSR {}.", ultimoNsr);
            ls.setUltimoNsr(ultimoNsr);
            return 0;
        }

        int count = 0;
        long maiorNsr = ultimoNsr;

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

                if (dto.getNsr() > maiorNsr) maiorNsr = dto.getNsr();

                // Envia comprovante por e-mail
                enviarComprovanteEmail(entity);
            }
        }

        ls.setUltimoNsr(maiorNsr);
        log.info("Batidas novas: {} | Maior NSR: {}", count, maiorNsr);
        return count;
    }

    /**
     * Envia comprovante por e-mail ao funcionário.
     * Só envia se:
     *   1. E-mail habilitado em /api/config
     *   2. Funcionário tem e-mail cadastrado
     * Marca email_enviado=true em ambos os casos para não repetir.
     */
    private void enviarComprovanteEmail(BatidaPonto batida) {
        try {
            if (!configService.isEmailHabilitado()) {
                batida.setEmailEnviado(true);
                batidaRepo.save(batida);
                return;
            }

            usuarioRepo.findByPisFormatado(batida.getPis())
                    .ifPresentOrElse(
                        usuario -> {
                            emailService.enviarComprovante(
                                    batida, usuario);
                            batida.setEmailEnviado(true);
                            batidaRepo.save(batida);
                        },
                        () -> {
                            log.debug("PIS {} sem cadastro.",
                                    batida.getPis());
                            batida.setEmailEnviado(true);
                            batidaRepo.save(batida);
                        }
                    );
        } catch (Exception e) {
            log.warn("Erro ao enviar comprovante NSR {}: {}",
                    batida.getNsr(), e.getMessage());
        }
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
                                           String status, int total,
                                           String mensagem) {
        ls.setStatus(status);
        ls.setDataFim(LocalDateTime.now());
        ls.setTotalRegistros(total);
        ls.setMensagem(mensagem);
        return logRepo.save(ls);
    }
}