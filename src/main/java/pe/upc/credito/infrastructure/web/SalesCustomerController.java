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
@RequestMapping("/sales")
@Tag(name = "Sales", description = "Customer credit summaries for checkout")
public class SalesCustomerController {

    private final CustomerService customers;
    private final TenantContext tenant;

    public SalesCustomerController(CustomerService customers, TenantContext tenant) {
        this.customers = customers;
        this.tenant = tenant;
    }

    @GetMapping("/customers")
    @Operation(summary = "List eligible customer profiles for checkout",
            description = "Returns current debt, credit limits and compensatory rate terms for the authenticated store.")
    public List<CustomerResponseDto> listCustomers() {
        return customers.list(tenant.currentStoreId());
    }
}
