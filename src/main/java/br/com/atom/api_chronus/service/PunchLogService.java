package br.com.atom.api_chronus.service;


import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;


import br.com.atom.api_chronus.dto.AfdLineDTO;
import br.com.atom.api_chronus.dto.AfdResponseDTO;
import br.com.atom.api_chronus.entity.BatidaPonto;
import br.com.atom.api_chronus.repository.BatidaPontoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço para buscar batidas de ponto.
 *
 * PASSO D: consulta o banco de dados PostgreSQL em vez do relógio.
 * O relógio só é acessado via SyncService (/api/sync).
 *
 * Vantagens:
 *   - Resposta instantânea (banco local vs HTTPS ao relógio)
 *   - Funciona offline (relógio desligado ou inacessível)
 *   - Não sobrecarrega o equipamento
 *   - Permite filtros mais complexos via JPA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PunchLogService {

    private final BatidaPontoRepository batidaRepo;

    /**
     * Busca todas as batidas do banco.
     * Substitui a chamada ao get_afd.fcgi do relógio.
     *
     * @param initialNsr ignorado — mantido para compatibilidade
     * @return AfdResponseDTO com todas as batidas do banco
     */
    public AfdResponseDTO buscarBatidas(Long initialNsr) {
        log.debug("Buscando batidas do banco de dados...");

        List<BatidaPonto> batidas = batidaRepo.findAll();

        if (batidas.isEmpty()) {
            log.warn("Nenhuma batida no banco. Execute /api/sync/batidas primeiro.");
            return new AfdResponseDTO(0, 0, Collections.emptyList());
        }

        // Converte entidades para DTOs
        List<AfdLineDTO> dtos = batidas.stream()
                .map(this::toDto)
                .toList();

        log.debug("Batidas encontradas no banco: {}", dtos.size());
        return new AfdResponseDTO(dtos.size(), dtos.size(), dtos);
    }

    /**
     * Busca batidas de um funcionário pelo PIS.
     *
     * @param pis PIS com ou sem zeros à esquerda
     * @return lista de batidas do funcionário
     */
    public List<AfdLineDTO> buscarPorPis(String pis) {
        if (pis == null || pis.isBlank()) return Collections.emptyList();

        String pisNorm = normalizarPis(pis);
        log.debug("Buscando batidas do banco para PIS: {}", pisNorm);

        return batidaRepo.findByPisOrderByDateTimeAsc(pisNorm)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Busca batidas de um funcionário em um período.
     *
     * @param pis   PIS do funcionário
     * @param inicio início do período
     * @param fim   fim do período
     * @return lista de batidas no período
     */
    public List<AfdLineDTO> buscarPorPisEPeriodo(String pis,
                                                  LocalDateTime inicio,
                                                  LocalDateTime fim) {
        String pisNorm = normalizarPis(pis);
        log.debug("Buscando batidas do banco. PIS: {} | {} a {}", pisNorm, inicio, fim);

        return batidaRepo.findByPisAndDateTimeBetweenOrderByDateTimeAsc(
                        pisNorm, inicio, fim)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Converte entidade BatidaPonto para AfdLineDTO.
     * Mantém compatibilidade com os serviços que usam AfdLineDTO.
     */
    private AfdLineDTO toDto(BatidaPonto entity) {
        return new AfdLineDTO(
                entity.getNsr(),
                entity.getDateTime(),
                entity.getTipo(),
                entity.getTipoDescricao(),
                entity.getPis(),
                entity.getLinhaOriginal()
        );
    }

    private String normalizarPis(String pis) {
        if (pis == null) return "000000000000";
        String soDigitos = pis.replaceAll("\\D", "");
        if (soDigitos.isEmpty()) return "000000000000";
        return String.format("%012d", Long.parseLong(soDigitos));
    }
}