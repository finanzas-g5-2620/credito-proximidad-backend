package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI proximityCreditOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Proximity Credit API")
                        .version("1.0.0")
                        .description("""
                                Manage stores, catalogs and customer credit accounts. Register sales,
                                calculate French amortization schedules, close billing cycles and record payments.
                                Store data is scoped to the caller's authenticated identity. Monetary amounts
                                are in PEN. Financial dates use the America/Lima business calendar.
                                """))
                .servers(List.of(new Server().url("/api/v1").description("Current host")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")
                                .description("Access token obtained from the login endpoint.")))
                .tags(List.of(
                        tag("Authentication", "Sign in and inspect the authenticated session."),
                        tag("Stores", "Register, update and suspend stores and their administrator accounts."),
                        tag("Customers", "Manage customer accounts and the original agreed credit terms."),
                        tag("Catalog", "Manage the store catalog, prices and inventory."),
                        tag("Finance", "Calculate installments and equivalent interest rates."),
                        tag("Sales", "Confirm sales and read sales history."),
                        tag("Settlements", "Close billing cycles, quote balances and receive payment."),
                        tag("Analytics", "Read key indicators, monthly flows and movements."),
                        tag("Demo Clock", "Control the clock in a dedicated academic test stack.")));
    }

    @Bean
    public OpenApiCustomizer describeAuthenticationResponses() {
        return api -> {
            if (api.getPaths() == null) {
                return;
            }
            api.getPaths().forEach((path, pathItem) -> pathItem.readOperations().forEach(operation -> {
                if ("/auth/login".equals(path)) {
                    operation.setSecurity(List.of());
                    operation.getResponses().addApiResponse("401",
                            new ApiResponse().description("Invalid credentials or inactive account."));
                    return;
                }
                operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER)));
                operation.getResponses().putIfAbsent("401",
                        new ApiResponse().description("Authentication is required or the token has expired."));
                operation.getResponses().putIfAbsent("403",
                        new ApiResponse().description("The authenticated account lacks permission."));
            }));
        };
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
