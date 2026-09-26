package com.incidentmind.recovery.policy;

import com.incidentmind.recovery.config.RecoveryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExponentialBackoffStrategy implements BackoffStrategy {

    private final RecoveryProperties properties;
    private final Sleeper sleeper;

    @org.springframework.beans.factory.annotation.Autowired
    public ExponentialBackoffStrategy(RecoveryProperties properties) {
        this(properties, Thread::sleep);
    }

    public ExponentialBackoffStrategy(RecoveryProperties properties, Sleeper sleeper) {
        this.properties = properties != null ? properties : new RecoveryProperties();
        this.sleeper = sleeper != null ? sleeper : Thread::sleep;
    }

    public ExponentialBackoffStrategy() {
        this(new RecoveryProperties(), Thread::sleep);
    }

    @Override
    public long calculateBackoffMs(int attemptNumber) {
        if (!properties.isExponentialBackoff() || attemptNumber <= 1) {
            return properties.getInitialBackoffMs();
        }

        double multiplier = Math.pow(properties.getBackoffMultiplier(), attemptNumber - 1);
        long calculated = (long) (properties.getInitialBackoffMs() * multiplier);
        return Math.min(calculated, properties.getMaxBackoffMs());
    }

    @Override
    public void applyBackoff(long backoffMs) throws InterruptedException {
        if (backoffMs > 0) {
            log.debug("Applying recovery backoff delay: {}ms", backoffMs);
            sleeper.sleep(backoffMs);
        }
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
