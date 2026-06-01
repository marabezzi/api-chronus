package br.com.atom.api_chronus.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.com.atom.api_chronus.dto.AfdLineDTO;
import br.com.atom.api_chronus.dto.AfdResponseDTO;
import br.com.atom.api_chronus.dto.UsuarioDTO;
import br.com.atom.api_chronus.entity.BatidaPonto;
import br.com.atom.api_chronus.entity.LogSincronizacao;
import br.com.atom.api_chronus.entity.UsuarioPonto;
import br.com.atom.api_chronus.repository.BatidaPontoRepository;
import br.com.atom.api_chronus.repository.LogSincronizacaoRepository;
import br.com.atom.api_chronus.repository.UsuarioPontoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de sincronização entre o relógio iDClass e o banco de dados.
 *
 * Executa dois tipos de sincronização:
 *
 *   1. AFD (batidas):
 *      - Busca o maior NSR já salvo no banco
 *      - Solicita ao relógio somente os registros a partir desse NSR
 *      - Insere apenas os registros novos (evita duplicatas pelo NSR)
 *      - Sincronização incremental — eficiente mesmo com 25.000 registros
 *
 *   2. Usuários:
 *      - Busca todos os usuários do relógio
 *      - Atualiza os existentes (pelo PIS) e insere os novos
 *      - Sincronização completa — poucos registros (~8 usuários)
 *
 * Agendamento: todos os dias à meia-noite (00:00).
 * Também pode ser disparado manualmente via SyncController.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final PunchLogService           punchLogService;
    private final UsuarioService            usuarioService;
    private final BatidaPontoRepository     batidaRepo;
    private final UsuarioPontoRepository    usuarioRepo;
    private final LogSincronizacaoRepository logRepo;

    // ── Agendamento automático ────────────────────────────────────────────

    /**
     * Sincronização completa agendada para meia-noite todos os dias.
     * Cron: "0 0 0 * * *" = segundo 0, minuto 0, hora 0, todo dia
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void sincronizacaoAutomatica() {
        log.info("=== Iniciando sincronização automática (meia-noite) ===");
        sincronizarCompleto();
    }

    // ── Métodos públicos (chamados pelo controller) ───────────────────────

    /**
     * Sincronização completa: usuários + batidas.
     *
     * @return log da sincronização
     */
    @Transactional
    public LogSincronizacao sincronizarCompleto() {
        LogSincronizacao log_sync = iniciarLog("COMPLETA");
        int total = 0;

        try {
            total += sincronizarUsuariosInterno();
            total += sincronizarBatidasInterno(log_sync);

            return finalizarLog(log_sync, "SUCESSO", total, null);
        } catch (Exception e) {
            log.error("Erro na sincronização completa: {}", e.getMessage(), e);
            return finalizarLog(log_sync, "ERRO", total, e.getMessage());
        }
    }

    /**
     * Sincroniza apenas os usuários do relógio.
     *
     * @return log da sincronização
     */
    @Transactional
    public LogSincronizacao sincronizarUsuarios() {
        LogSincronizacao log_sync = iniciarLog("USUARIOS");
        try {
            int total = sincronizarUsuariosInterno();
            return finalizarLog(log_sync, "SUCESSO", total, null);
        } catch (Exception e) {
            log.error("Erro ao sincronizar usuários: {}", e.getMessage(), e);
            return finalizarLog(log_sync, "ERRO", 0, e.getMessage());
        }
    }

    /**
     * Sincroniza apenas as batidas de ponto (incremental).
     *
     * @return log da sincronização
     */
    @Transactional
    public LogSincronizacao sincronizarBatidas() {
        LogSincronizacao log_sync = iniciarLog("AFD");
        try {
            int total = sincronizarBatidasInterno(log_sync);
            return finalizarLog(log_sync, "SUCESSO", total, null);
        } catch (Exception e) {
            log.error("Erro ao sincronizar batidas: {}", e.getMessage(), e);
            return finalizarLog(log_sync, "ERRO", 0, e.getMessage());
        }
    }

    // ── Implementações internas ───────────────────────────────────────────

    /**
     * Sincroniza usuários do relógio para o banco.
     * Atualiza existentes e insere novos.
     */
    private int sincronizarUsuariosInterno() {
        log.info("Sincronizando usuários...");
        List<UsuarioDTO> usuarios = usuarioService.listarTodos();

        if (usuarios.isEmpty()) {
            log.warn("Nenhum usuário retornado pelo relógio.");
            return 0;
        }

        int count = 0;
        for (UsuarioDTO dto : usuarios) {
            // Busca pelo PIS — atualiza se existir, insere se não existir
            Optional<UsuarioPonto> existente = usuarioRepo.findByPis(dto.getPis());
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

            usuarioRepo.save(entity);
            count++;
        }

        log.info("Usuários sincronizados: {}", count);
        return count;
    }

    /**
     * Sincroniza batidas de ponto de forma incremental.
     * Busca apenas registros com NSR maior que o último sincronizado.
     */
    private int sincronizarBatidasInterno(LogSincronizacao log_sync) {
        // Descobre o maior NSR já no banco
        Long ultimoNsr = batidaRepo.findMaxNsr().orElse(0L);
        log.info("Sincronizando batidas. Último NSR no banco: {}", ultimoNsr);

        // Busca do relógio a partir do próximo NSR
        AfdResponseDTO afd = punchLogService.buscarBatidas(ultimoNsr + 1);

        if (afd == null || afd.getBatidas() == null || afd.getBatidas().isEmpty()) {
            log.info("Nenhuma batida nova desde NSR {}.", ultimoNsr);
            log_sync.setUltimoNsr(ultimoNsr);
            return 0;
        }

        int count = 0;
        long maiorNsr = ultimoNsr;

        for (AfdLineDTO dto : afd.getBatidas()) {
            // Garante que não há duplicatas (NSR é único)
            if (!batidaRepo.existsByNsr(dto.getNsr())) {
                BatidaPonto entity = new BatidaPonto();
                entity.setNsr(dto.getNsr());
                entity.setPis(dto.getPis());
                entity.setDateTime(dto.getDateTime());
                entity.setTipo(dto.getTipo());
                entity.setTipoDescricao(dto.getTipoDescricao());
                entity.setLinhaOriginal(dto.getLinhaOriginal());
                entity.setCriadoEm(LocalDateTime.now());

                batidaRepo.save(entity);
                count++;

                if (dto.getNsr() > maiorNsr) maiorNsr = dto.getNsr();
            }
        }

        log_sync.setUltimoNsr(maiorNsr);
        log.info("Batidas novas inseridas: {} | Maior NSR: {}", count, maiorNsr);
        return count;
    }

    // ── Helpers de log ────────────────────────────────────────────────────

    private LogSincronizacao iniciarLog(String tipo) {
        LogSincronizacao ls = new LogSincronizacao();
        ls.setTipo(tipo);
        ls.setStatus("EXECUTANDO");
        ls.setDataInicio(LocalDateTime.now());
        ls.setTotalRegistros(0);
        return logRepo.save(ls);
    }

    private LogSincronizacao finalizarLog(LogSincronizacao ls, String status,
                                          int total, String mensagem) {
        ls.setStatus(status);
        ls.setDataFim(LocalDateTime.now());
        ls.setTotalRegistros(total);
        ls.setMensagem(mensagem);
        return logRepo.save(ls);
    }
}