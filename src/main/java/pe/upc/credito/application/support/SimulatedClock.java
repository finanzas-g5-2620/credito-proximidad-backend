package pe.upc.credito.application.support;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reloj de negocio con desplazamiento simulado.
 *
 * <p>Los endpoints {@code /__mock/advance-date} y {@code /__mock/run-cutoff}
 * existen en el contrato del frontend para demostrar el ciclo mensual sin
 * esperar fechas reales. El desplazamiento vive en memoria: al reiniciar el
 * backend vuelve a la fecha real. Es deliberado, para no alterar el esquema
 * del capitulo 8 con una tabla de parametros.</p>
 */
@Component
public class SimulatedClock {

    private final Clock baseClock = Clock.systemUTC();
    private final AtomicLong offsetDays = new AtomicLong(0);

    public Instant now() {
        return baseClock.instant().plus(offsetDays.get(), ChronoUnit.DAYS);
    }

    public LocalDate today() {
        return LocalDate.now(baseClock).plusDays(offsetDays.get());
    }

    public LocalDate advance(int days) {
        offsetDays.addAndGet(days);
        return today();
    }

    public long offsetDays() {
        return offsetDays.get();
    }

    public void reset() {
        offsetDays.set(0);
    }
}
