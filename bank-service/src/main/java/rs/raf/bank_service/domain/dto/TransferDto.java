package rs.raf.bank_service.domain.dto;

import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class TransferDto {

    @NotBlank(message = "Sender account number is required.")
    private String senderAccountNumber;

    @NotBlank(message = "Receiver account number is required.")
    private String receiverAccountNumber;

    @NotNull(message = "Amount is required.")
    @Positive(message = "Amount must be positive.")
    private BigDecimal amount;
}
