package pe.upc.credito.application.service;

import org.springframework.stereotype.Service;
import pe.upc.credito.application.dto.AdvanceDateResponseDto;
import pe.upc.credito.application.dto.RunCutoffResponseDto;
import pe.upc.credito.application.support.SimulatedClock;

import java.time.LocalDate;

/**
 * Operaciones de simulacion temporal usadas por la demo del frontend.
 *
 * <p>Permiten recorrer el ciclo mensual (corte, vencimiento, mora y pago) sin
 * esperar fechas reales. El desplazamiento del reloj es en memoria y se pierde
 * al reiniciar el backend.</p>
 */
@Service
public class MockService {

    private final SettlementService settlements;
    private final SimulatedClock clock;

    public MockService(SettlementService settlements, SimulatedClock clock) {
        this.settlements = settlements;
        this.clock = clock;
    }

    /** Ejecuta el corte mensual para el tenant actual. */
    public RunCutoffResponseDto runCutoff(Integer storeId) {
        return settlements.runCutoff(storeId);
    }

    /** Adelanta el reloj simulado y devuelve la nueva fecha del sistema. */
    public AdvanceDateResponseDto advanceDate(int days) {
        LocalDate newDate = clock.advance(days);
        return new AdvanceDateResponseDto(newDate.toString());
    }
}
