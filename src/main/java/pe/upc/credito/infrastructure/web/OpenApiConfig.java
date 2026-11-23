package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Documentacion OpenAPI 3 expuesta por springdoc.
 *
 * <p>UI: {@code http://localhost:8081/api/v1/swagger-ui.html}<br>
 * JSON: {@code http://localhost:8081/api/v1/v3/api-docs}</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI creditoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Credito de Proximidad API")
                        .version("1.0.0")
                        .description("""
                                Backend del sistema de credito de proximidad (UPC 1ASI0642 - Grupo 5).

                                Implementa el modelo relacional del capitulo 8 sobre PostgreSQL, el motor
                                de amortizacion frances con base comercial 360/30 y las reglas de cobranza
                                del Codigo Civil (Arts. 1250 y 1257).

                                **Multi-tenant:** todas las consultas se filtran por tienda. El header
                                `X-Store-Id` es opcional; si no se envia se usa la tienda 1.
                                """)
                        .contact(new Contact()
                                .name("Grupo 5 - 1ASI0642")
                                .email("grupo5@upc.edu.pe")))
                .servers(List.of(
                        new Server().url("http://localhost:8081/api/v1").description("Entorno local"),
                        new Server().url("http://credito-backend:8081/api/v1").description("Red Docker")))
                .tags(List.of(
                        new Tag().name("Finanzas").description("Simulacion del cronograma frances"),
                        new Tag().name("Clientes").description("Cuentas corrientes y linea de credito"),
                        new Tag().name("Catalogo").description("Productos vendibles"),
                        new Tag().name("Ventas").description("Registro y consulta de ventas"),
                        new Tag().name("Liquidaciones").description("Cobranza y estados de cuenta"),
                        new Tag().name("Analitica").description("KPIs y auditoria"),
                        new Tag().name("Simulacion").description("Reloj y corte para la demo")));
    }

    /**
     * Agrega el header {@code X-Store-Id} a todas las operaciones para que se
     * pueda probar el aislamiento multi-tenant desde Swagger UI.
     */
    @Bean
    public OperationCustomizer storeHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() == null) {
                operation.setParameters(new ArrayList<>());
            }
            boolean alreadyPresent = operation.getParameters().stream()
                    .anyMatch(parameter -> "X-Store-Id".equals(parameter.getName()));
            if (!alreadyPresent) {
                IntegerSchema schema = new IntegerSchema();
                schema.setDefault(1);

                HeaderParameter header = new HeaderParameter();
                header.setName("X-Store-Id");
                header.setIn("header");
                header.setRequired(false);
                header.setDescription("Identificador de la tienda (tenant). Por defecto 1.");
                header.setSchema(schema);

                operation.addParametersItem(header);
            }
            return operation;
        };
    }
}
