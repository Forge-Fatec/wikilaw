package forge.wikilaw.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import forge.wikilaw.backend.config.JurisprudenciaBootstrapProperties;
import forge.wikilaw.backend.integration.datajud.DataJudProperties;
import forge.wikilaw.backend.integration.embedding.EmbeddingProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({DataJudProperties.class, JurisprudenciaBootstrapProperties.class,
		EmbeddingProperties.class})
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
