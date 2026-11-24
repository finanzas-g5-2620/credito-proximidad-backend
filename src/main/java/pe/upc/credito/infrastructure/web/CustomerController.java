package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.CustomerCreateRequestDto;
import pe.upc.credito.application.dto.CustomerProfileResponseDto;
import pe.upc.credito.application.dto.CustomerUpdateRequestDto;
import pe.upc.credito.application.service.CustomerService;
import pe.upc.credito.application.support.TenantContext;

import java.util.List;

@RestController
@RequestMapping("/customers")
@Tag(name = "Customers", description = "Credit accounts and profiles in the authenticated store")
public class CustomerController {

    private final CustomerService customerService;
    private final TenantContext tenant;

    public CustomerController(CustomerService customerService, TenantContext tenant) {
        this.customerService = customerService;
        this.tenant = tenant;
    }

    @GetMapping
    @Operation(summary = "List accessible customer profiles",
            description = "Returns profiles for the current store. A customer receives only their own profile.")
    public List<CustomerProfileResponseDto> list() {
        if (tenant.isCustomer()) {
            return List.of(customerService.getProfile(tenant.currentStoreId(), tenant.currentCustomerId()));
        }
        return customerService.listProfiles(tenant.currentStoreId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read a customer credit profile",
            description = "Returns the customer profile and original credit terms when visible to the caller.")
    public CustomerProfileResponseDto get(@PathVariable Integer id) {
        tenant.requireCustomerOwner(id);
        return customerService.getProfile(tenant.currentStoreId(), id);
    }

    @PostMapping
    @Operation(summary = "Create a customer and account",
            description = "Creates the customer login and credit profile for the authenticated store in a single transaction.")
    public CustomerProfileResponseDto create(@Valid @RequestBody CustomerCreateRequestDto request) {
        return customerService.create(tenant.currentStoreId(), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a customer credit profile",
            description = "Updates contact data and eligible credit terms. Pending debt restricts changes to interest and billing dates.")
    public CustomerProfileResponseDto update(@PathVariable Integer id,
                                             @Valid @RequestBody CustomerUpdateRequestDto request) {
        return customerService.update(tenant.currentStoreId(), id, request);
    }
}
