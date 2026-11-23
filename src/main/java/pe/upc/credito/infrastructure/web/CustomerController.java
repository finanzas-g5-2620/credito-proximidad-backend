package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.CustomerResponseDto;
import pe.upc.credito.application.service.CustomerService;
import pe.upc.credito.application.support.TenantContext;

import java.util.List;

@RestController
@RequestMapping("/customers")
@Tag(name = "Clientes", description = "Cuentas corrientes y linea de credito disponible")
public class CustomerController {

    private final CustomerService customerService;
    private final TenantContext tenant;

    public CustomerController(CustomerService customerService, TenantContext tenant) {
        this.customerService = customerService;
        this.tenant = tenant;
    }

    @GetMapping
    @Operation(summary = "Listar clientes de la tienda",
            description = "Devuelve la linea de credito, la deuda vigente y si el cliente "
                    + "puede acceder a la modalidad de pago mensual.")
    public List<CustomerResponseDto> list() {
        return customerService.list(tenant.currentStoreId());
    }
}
