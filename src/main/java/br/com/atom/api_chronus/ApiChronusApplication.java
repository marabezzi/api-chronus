package br.com.atom.api_chronus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling: habilita o agendamento do @Scheduled no SyncService
 */
@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
public class ApiChronusApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiChronusApplication.class, args);
    }
}