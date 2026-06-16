package in.bushansirgur.cloudshareapi.controller;

import in.bushansirgur.cloudshareapi.document.PaymentTransaction;
import in.bushansirgur.cloudshareapi.repository.PaymentTransactionRepository;
import in.bushansirgur.cloudshareapi.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SecurityUtil securityUtil;

    @GetMapping
    public ResponseEntity<?> getUserTransactions() {
        String userId = securityUtil.getCurrentUserId();
        List<PaymentTransaction> transactions =
                paymentTransactionRepository.findByUserIdAndStatusOrderByTransactionDateDesc(userId, "SUCCESS");
        return ResponseEntity.ok(transactions);
    }
}
