package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.StoreCreateRequestDto;
import pe.upc.credito.application.dto.StorePatchRequestDto;
import pe.upc.credito.application.dto.StoreResponseDto;
import pe.upc.credito.application.dto.StoreUpdateRequestDto;
import pe.upc.credito.application.service.StoreService;

import java.util.List;

@RestController
@RequestMapping("/stores")
@Tag(name = "Stores", description = "Global administration of stores and their administrator accounts")
public class StoreController {

    private final StoreService stores;

    public StoreController(StoreService stores) {
        this.stores = stores;
    }

    @GetMapping
    @Operation(summary = "List stores",
            description = "Returns stores and their operational status to the global administrator.")
    public List<StoreResponseDto> list() {
        return stores.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read a store",
            description = "Returns the store profile for the supplied store identifier.")
    public StoreResponseDto get(@PathVariable Integer id) {
        return stores.get(id);
    }

    @PostMapping
    @Operation(summary = "Register a store and administrator",
            description = "Creates the store and its administrator account in a single transaction.")
    public StoreResponseDto create(@Valid @RequestBody StoreCreateRequestDto request) {
        return stores.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a store",
            description = "Replaces the editable store profile.")
    public StoreResponseDto update(@PathVariable Integer id,
                                   @Valid @RequestBody StoreUpdateRequestDto request) {
        return stores.update(id, request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change store details or status",
            description = "Updates selected fields, including activation or suspension.")
    public StoreResponseDto patch(@PathVariable Integer id,
                                  @Valid @RequestBody StorePatchRequestDto request) {
        return stores.patch(id, request);
    }
}
