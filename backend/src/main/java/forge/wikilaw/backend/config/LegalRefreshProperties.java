package forge.wikilaw.backend.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("wikilaw.refresh")
public class LegalRefreshProperties {
    private boolean enabled = true;
    private Duration interval = Duration.ofDays(1);
    @Min(1) @Max(100)
    private int pageSize = 20;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getInterval() { return interval; }
    public void setInterval(Duration interval) { this.interval = interval; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    @AssertTrue(message = "wikilaw.refresh.interval deve ser de pelo menos 1 minuto")
    public boolean isIntervalValid() {
        return interval != null && interval.compareTo(Duration.ofMinutes(1)) >= 0;
    }
}
