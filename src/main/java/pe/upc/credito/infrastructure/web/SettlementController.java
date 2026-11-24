package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.PaymentRequestDto;
import pe.upc.credito.application.dto.PaymentResponseDto;
import pe.upc.credito.application.dto.PayoffQuoteResponseDto;
import pe.upc.credito.application.dto.RunCutoffResponseDto;
import pe.upc.credito.application.dto.SettlementResponseDto;
import pe.upc.credito.application.service.SettlementService;

import java.util.List;

@RestController
@RequestMapping("/settlements")
@Tag(name = "Settlements", description = "Monthly billing, payoff quotes, cutoff processing and payments")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping
    @Operation(summary = "List settlements",
            description = "Returns settlements visible to the authenticated customer or store administrator.")
    @ApiResponse(responseCode = "200", description = "Visible settlements returned")
    public List<SettlementResponseDto> list() {
        return settlementService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a settlement",
            description = "Returns one settlement within the authenticated customer's or store's scope.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settlement found"),
            @ApiResponse(responseCode = "404", description = "Settlement not found in the authenticated scope")
    })
    public SettlementResponseDto get(@PathVariable Integer id) {
        return settlementService.get(id);
    }

    @GetMapping("/{id}/payoff-quote")
    @Operation(summary = "Quote full settlement payoff",
            description = "Calculates remaining principal, compensatory interest and principal-only late penalty. "
                    + "Customers may quote only their own settlement. If target_date is omitted, today's "
                    + "business date is used.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payoff quote calculated"),
            @ApiResponse(responseCode = "404", description = "Settlement not found in the authenticated scope"),
            @ApiResponse(responseCode = "409", description = "Settlement is already paid"),
            @ApiResponse(responseCode = "403", description = "Customer is outside the authenticated scope")
    })
    public PayoffQuoteResponseDto payoffQuote(
            @PathVariable Integer id,
            @Parameter(description = "Quote date in yyyy-MM-dd format", example = "2026-12-20")
            @RequestParam(name = "target_date", required = false) String targetDate) {
        return settlementService.payoffQuote(id, targetDate);
    }

    @PostMapping("/cutoff")
    @Operation(summary = "Run monthly cutoff",
            description = "STORE_ADMIN only. Bills all eligible installment rows and month-end purchases whose "
                    + "contractual billing_cutoff_date is on or before the previous business day, so the full "
                    + "cutoff day has closed. Repeated runs do not duplicate a customer's monthly settlement.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cutoff completed"),
            @ApiResponse(responseCode = "403", description = "Only a store administrator can run cutoff")
    })
    public RunCutoffResponseDto runCutoff() {
        return settlementService.runCutoff();
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Register full settlement payment",
            description = "STORE_ADMIN only. The received amount must cover the current full balance. Any excess "
                    + "is credited to the customer's favour balance. Idempotency-Key is required so retries "
                    + "cannot register the same collection twice.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment registered or idempotent retry returned"),
            @ApiResponse(responseCode = "400", description = "Invalid amount or missing Idempotency-Key"),
            @ApiResponse(responseCode = "403", description = "Only a store administrator can collect payments"),
            @ApiResponse(responseCode = "404", description = "Settlement not found in the authenticated store"),
            @ApiResponse(responseCode = "409", description = "Settlement paid or Idempotency-Key reused with other data"),
            @ApiResponse(responseCode = "422", description = "Received amount does not cover the full balance")
    })
    public PaymentResponseDto pay(
            @PathVariable Integer id,
            @Parameter(description = "Stable client-generated key for this collection; reuse it only for retries",
                    required = true, example = "6d0d94ec-c087-42a5-9f29-4340c3af0b67")
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody PaymentRequestDto request) {
        return settlementService.pay(id, request, idempotencyKey);
    }
}
