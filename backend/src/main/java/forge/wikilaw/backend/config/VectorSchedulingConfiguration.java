package forge.wikilaw.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Inference must not hold up import pages or semantic preparation on their scheduler. */
@Configuration
@ConditionalOnProperty(name="wikilaw.vector.enabled",havingValue="true")
public class VectorSchedulingConfiguration {
    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("wikilaw-import-");
        return scheduler;
    }
    @Bean("vectorTaskScheduler")
    public ThreadPoolTaskScheduler vectorTaskScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("wikilaw-vector-");
        return scheduler;
    }
}
