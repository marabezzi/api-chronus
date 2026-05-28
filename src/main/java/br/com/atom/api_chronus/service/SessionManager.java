package br.com.atom.api_chronus.service;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

import br.com.atom.api_chronus.dto.LoginResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gerencia o token de sessão do relógio iDClass em memória.
 *
 * Responsabilidades:
 *   - Guardar o token e o momento em que foi obtido
 *   - Verificar se o token ainda é válido antes de cada uso
 *   - Renovar automaticamente quando expirar
 *   - Garantir thread-safety: apenas uma thread renova por vez
 *
 * Por que em memória e não em banco?
 *   O token é temporário e específico desta instância da API.
 *   Não faz sentido persistir — ao reiniciar, um novo login é feito.
 *
 * Tempo de expiração:
 *   O iDClass não documenta o TTL exato do token.
 *   Usamos 8 minutos como margem segura (valor conservador).
 *   Ajuste TOKEN_TTL_SEGUNDOS conforme seu ambiente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionManager {

  /** Tempo de vida do token em segundos (8 minutos de margem segura) */
    private static final long TOKEN_TTL_SEGUNDOS = 480;

    /** Serviço que realiza o login no relógio */
    private final IdClassAuthService authService;

    /** Token de sessão atual — null se ainda não autenticado */
    private volatile String sessionToken = null;

    /**
     * Momento em que o token foi obtido.
     * Instant.MIN garante que na primeira chamada o token seja considerado expirado.
     */
    private volatile Instant tokenObtidoEm = Instant.MIN;

    /**
     * Lock para evitar que múltiplas threads façam login simultâneo.
     * Sem isso, sob carga, várias threads poderiam tentar renovar ao mesmo tempo.
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Retorna um token de sessão válido.
     *
     * Se o token atual ainda for válido, retorna ele.
     * Se expirou ou não existe, faz novo login e retorna o novo token.
     *
     * @return token de sessão válido, ou null se o login falhar
     */
    public String getSessionValida() {

        // Verificação rápida sem lock (caminho feliz — token ainda válido)
        if (tokenValido()) {
            log.debug("Reutilizando token de sessão existente.");
            return sessionToken;
        }

        // Token expirado ou ausente — precisa renovar com lock
        lock.lock();
        try {
            // Double-check: outra thread pode ter renovado enquanto esperávamos o lock
            if (tokenValido()) {
                log.debug("Token renovado por outra thread — reutilizando.");
                return sessionToken;
            }

            // Faz o login e atualiza o token
            log.info("Token de sessão expirado ou ausente — fazendo novo login.");
            return renovarToken();

        } finally {
            // Libera o lock SEMPRE, mesmo se renovarToken() lançar exceção
            lock.unlock();
        }
    }

    /**
     * Força a renovação do token independente do TTL.
     * Útil quando o relógio retorna erro de sessão inválida (401/403).
     *
     * @return novo token de sessão, ou null se o login falhar
     */
    public String renovarTokenForcado() {
        lock.lock();
        try {
            log.info("Renovação forçada do token de sessão.");
            return renovarToken();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Verifica se há uma sessão ativa no momento.
     * Usado pelo endpoint de status.
     *
     * @return true se o token existir e não tiver expirado
     */
    public boolean temSessaoAtiva() {
        return tokenValido();
    }

    /**
     * Retorna o momento em que o token atual foi obtido.
     * Retorna null se não houver token.
     */
    public Instant getTokenObtidoEm() {
        return sessionToken != null ? tokenObtidoEm : null;
    }

    /**
     * Retorna quantos segundos faltam para o token expirar.
     * Retorna 0 se o token já expirou ou não existe.
     */
    public long getSegundosRestantes() {
        if (!tokenValido()) return 0;
        long decorrido = Instant.now().getEpochSecond() - tokenObtidoEm.getEpochSecond();
        return Math.max(0, TOKEN_TTL_SEGUNDOS - decorrido);
    }

    // ── Métodos privados ────────────────────────────────────────────────────

    /**
     * Verifica se o token atual ainda está dentro do TTL.
     */
    private boolean tokenValido() {
        if (sessionToken == null) return false;
        long segundosDecorridos = Instant.now().getEpochSecond() - tokenObtidoEm.getEpochSecond();
        return segundosDecorridos < TOKEN_TTL_SEGUNDOS;
    }

    /**
     * Executa o login no relógio e atualiza o token em memória.
     * Deve ser chamado sempre dentro do lock.
     *
     * @return novo token, ou null se o login falhar
     */
    private String renovarToken() {
        LoginResponseDTO response = authService.login();

        if (response != null && response.getSession() != null) {
            sessionToken    = response.getSession();
            tokenObtidoEm   = Instant.now();
            log.info("Token de sessão renovado com sucesso. Expira em {} segundos.",
                    TOKEN_TTL_SEGUNDOS);
            return sessionToken;
        }

        // Login falhou — invalida o token atual para forçar nova tentativa
        sessionToken  = null;
        tokenObtidoEm = Instant.MIN;
        log.error("Falha ao renovar token de sessão. Verifique credenciais e conectividade.");
        return null;
    }
}