package br.com.atom.api_chronus.repository;

import br.com.atom.api_chronus.entity.ConfiguracaoSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfiguracaoSistemaRepository
        extends JpaRepository<ConfiguracaoSistema, Long> {

    Optional<ConfiguracaoSistema> findByChave(String chave);

    List<ConfiguracaoSistema> findByCategoriaOrderByChaveAsc(
            String categoria);
}