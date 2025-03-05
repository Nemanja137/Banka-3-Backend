package rs.raf.bank_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rs.raf.bank_service.domain.dto.CreatePaymentDto;
import rs.raf.bank_service.domain.dto.TransferDto;
import rs.raf.bank_service.service.PaymentService;
import rs.raf.bank_service.exceptions.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PreAuthorize("hasAuthority('client')")
    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds between accounts", description = "Transfers funds from one account to another. Both must " +
            "be using the same currency.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transfer successful"),
            @ApiResponse(responseCode = "400", description = "Invalid input data, not same currency or insufficient funds"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "422", description = "Validation or transfer creation error"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> createTransfer(
            @Valid @RequestBody TransferDto dto,
            @RequestHeader("Authorization") String token) {

        Long clientId = paymentService.getClientIdFromToken(token);

        try {
            boolean success = paymentService.createTransferPendingConformation(dto, clientId);
            if (success) {
                return ResponseEntity.status(HttpStatus.OK).body("Transfer created successfully, waiting for confirmation.");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Transfer creation failed: Insufficient funds or invalid data");
            }
        } catch (SenderAccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sender account not found: " + e.getMessage());
        } catch (ReceiverAccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Receiver account not found: " + e.getMessage());
        } catch (InsufficientFundsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Insufficient funds: " + e.getMessage());
        } catch (NotSameCurrencyForTransferException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Currency mismatch: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('client')")
    @PostMapping("/confirm-transfer/{paymentId}")
    @Operation(summary = "Confirm and execute transfer", description = "Confirm transfer and execute funds transfer between accounts after verification.")
    public ResponseEntity<String> confirmTransfer(@PathVariable Long paymentId,
                                                  @RequestHeader("Authorization") String token) {
        Long clientId = paymentService.getClientIdFromToken(token);
        try {
            boolean success = paymentService.confirmTransferAndExecute(paymentId, clientId);
            if (success) {
                return ResponseEntity.status(HttpStatus.OK).body("Transfer completed successfully.");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Transfer failed.");
            }
        } catch (PaymentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Payment not found: " + e.getMessage());
        } catch (UnauthorizedTransferConormationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized access: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to complete transfer: " + e.getMessage());
        }
    }

    //Metoda za zapocinjanje placanja, al ne izvrsava je sve dok se ne odradi verifikacija pa se odradjuje druga metoda.
    @PostMapping("/payment")
    @Operation(summary = "Make a payment", description = "Executes a payment from the sender's account.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data or insufficient funds"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "422", description = "Payment creation error"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> newPayment(
            @Valid @RequestBody CreatePaymentDto dto,
            @RequestHeader("Authorization") String token) {
        Long clientId = paymentService.getClientIdFromToken(token);
        try {
            boolean success = paymentService.createPaymentBeforeConformation(dto, clientId);
            if (success) {
                return ResponseEntity.status(HttpStatus.OK).body("Payment created successfully");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Payment failed: Insufficient funds or invalid data");
            }
        } catch (SenderAccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sender account not found: " + e.getMessage());
        } catch (InsufficientFundsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Insufficient funds: " + e.getMessage());
        } catch (PaymentCodeNotProvidedException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Payment code is required: " + e.getMessage());
        } catch (PurposeOfPaymentNotProvidedException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Purpose of payment is required: " + e.getMessage());
        } catch (SendersAccountsCurencyIsNotDinarException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Sender's account must be in RSD: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    // Metoda za potvrdu plaćanja
    @PreAuthorize("hasAuthority('client')")
    @PostMapping("/confirm-payment/{paymentId}")
    @Operation(summary = "Confirm payment", description = "Confirm and execute payment once the receiver is verified.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment completed successfully"),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "400", description = "Payment validation error"),
            @ApiResponse(responseCode = "500", description = "Internal server error while processing payment")
    })
    public ResponseEntity<String> confirmPayment(@PathVariable Long paymentId,
                                                 @RequestHeader("Authorization") String token) {
        Long clientId = paymentService.getClientIdFromToken(token);
        try {
            paymentService.confirmPayment(paymentId, clientId);
            return ResponseEntity.status(HttpStatus.OK).body("Payment completed successfully.");
        } catch (PaymentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Payment not found: " + e.getMessage());
        } catch (UnauthorizedPaymentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized access: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to complete payment: " + e.getMessage());
        }
    }
}