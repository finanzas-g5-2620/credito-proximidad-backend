package pe.upc.credito.application.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Business clock with an optional in-memory date for demo and integration runs. */
@Component
public class SimulatedClock {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");
    private final Clock baseClock = Clock.system(BUSINESS_ZONE);
    private final boolean demoEnabled;
    private final LocalDate demoStartDate;
    private final AtomicLong offsetDays = new AtomicLong(0);

    public SimulatedClock(@Value("${app.demo-clock.enabled:false}") boolean demoEnabled,
                          @Value("${app.demo-clock.date:2026-11-26}") String demoDate) {
        this.demoEnabled = demoEnabled;
        this.demoStartDate = LocalDate.parse(demoDate);
    }

    public Instant now() {
        if (demoEnabled) {
            return today().atTime(LocalTime.now(baseClock)).atZone(BUSINESS_ZONE).toInstant()
                    .truncatedTo(ChronoUnit.MICROS);
        }
        return baseClock.instant().plus(offsetDays.get(), ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
    }

    public LocalDate today() {
        if (demoEnabled) {
            return demoStartDate.plusDays(offsetDays.get());
        }
        return LocalDate.now(baseClock).plusDays(offsetDays.get());
    }

    public LocalDate advance(int days) {
        if (!demoEnabled) {
            throw new IllegalStateException("Demo clock is disabled");
        }
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("Advance days must be between 1 and 365");
        }
        offsetDays.addAndGet(days);
        return today();
    }

    public boolean demoEnabled() {
        return demoEnabled;
    }

    public long offsetDays() {
        return offsetDays.get();
    }

    public void reset() {
        offsetDays.set(0);
    }
}
