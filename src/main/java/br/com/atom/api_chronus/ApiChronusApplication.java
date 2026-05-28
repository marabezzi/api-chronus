package br.com.atom.api_chronus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class ApiChronusApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiChronusApplication.class, args);
	}

}
