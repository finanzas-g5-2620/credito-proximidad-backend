package pe.upc.credito;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend de Credito de Proximidad.
 *
 * <p>Arquitectura por capas (cap. 9 del documento del proyecto):</p>
 * <ul>
 *   <li>{@code pe.upc.credito.domain} - nucleo puro de negocio (entidades, enums, reglas financieras)</li>
 *   <li>{@code pe.upc.credito.application} - casos de uso y orquestacion (CQRS)</li>
 *   <li>{@code pe.upc.credito.infrastructure} - adaptadores: persistencia PostgreSQL, seguridad, logging</li>
 *   <li>{@code pe.upc.credito.presentation.webapi} - controladores REST, middlewares, CORS</li>
 * </ul>
 */
@SpringBootApplication
public class BackendApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApiApplication.class, args);
    }
}
