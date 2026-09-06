package com.designpattern.cognitorbac.messaging.outbox;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Configuration shared by the reusable MongoDB-outbox-to-SQS adapter. */
@Validated
@ConfigurationProperties(prefix = "messaging.outbox")
public class OutboxProperties {
    private boolean enabled;
    @NotBlank
    private String region = "us-east-1";
    private String queueUrl;
    @NotBlank
    private String source = "application";
    @NotBlank
    private String fifoMessageGroupId = "domain-events";
    @NotNull
    private Duration pollInterval = Duration.ofSeconds(1);
    @Min(1) @Max(100)
    private int batchSize = 10;
    @NotNull
    private Duration leaseDuration = Duration.ofSeconds(30);
    @NotNull
    private Duration publishTimeout = Duration.ofSeconds(10);
    @NotNull
    private Duration publishAttemptTimeout = Duration.ofSeconds(3);
    @Min(1)
    private int maxAttempts = 10;
    @NotNull
    private Duration initialRetryDelay = Duration.ofSeconds(2);
    @NotNull
    private Duration maxRetryDelay = Duration.ofMinutes(5);

    @AssertTrue(message = "messaging.outbox.queue-url is required when the outbox is enabled")
    public boolean isQueueConfiguredWhenEnabled() {
        return !enabled || queueUrl != null && !queueUrl.isBlank();
    }

    @AssertTrue(message = "messaging.outbox durations must be positive")
    public boolean isDurationConfigurationValid() {
        return positive(pollInterval) && positive(leaseDuration)
                && positive(publishTimeout) && positive(publishAttemptTimeout)
                && positive(initialRetryDelay) && positive(maxRetryDelay)
                && publishAttemptTimeout.compareTo(publishTimeout) <= 0
                && publishTimeout.compareTo(leaseDuration) < 0
                && maxRetryDelay.compareTo(initialRetryDelay) >= 0;
    }

    private boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getQueueUrl() { return queueUrl; }
    public void setQueueUrl(String queueUrl) { this.queueUrl = queueUrl; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getFifoMessageGroupId() { return fifoMessageGroupId; }
    public void setFifoMessageGroupId(String fifoMessageGroupId) { this.fifoMessageGroupId = fifoMessageGroupId; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public Duration getPublishTimeout() { return publishTimeout; }
    public void setPublishTimeout(Duration publishTimeout) { this.publishTimeout = publishTimeout; }
    public Duration getPublishAttemptTimeout() { return publishAttemptTimeout; }
    public void setPublishAttemptTimeout(Duration publishAttemptTimeout) { this.publishAttemptTimeout = publishAttemptTimeout; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getInitialRetryDelay() { return initialRetryDelay; }
    public void setInitialRetryDelay(Duration initialRetryDelay) { this.initialRetryDelay = initialRetryDelay; }
    public Duration getMaxRetryDelay() { return maxRetryDelay; }
    public void setMaxRetryDelay(Duration maxRetryDelay) { this.maxRetryDelay = maxRetryDelay; }
}
