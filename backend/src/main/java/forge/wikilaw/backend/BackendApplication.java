package forge.wikilaw.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import forge.wikilaw.backend.integration.datajud.DataJudProperties;

@SpringBootApplication
@EnableConfigurationProperties(DataJudProperties.class)
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
