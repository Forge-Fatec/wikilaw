package forge.wikilaw.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"wikilaw.vector.enabled=true","wikilaw.vector.initial-delay=3600000"})
class VectorSchedulingTest {
    @Autowired @Qualifier("taskScheduler") TaskScheduler imports;
    @Autowired @Qualifier("vectorTaskScheduler") TaskScheduler vectors;
    @Test void inferenceAndImportsHaveIndependentSchedulers() { assertThat(vectors).isNotSameAs(imports); }
}
