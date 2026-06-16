package in.bushansirgur.cloudshareapi.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import in.bushansirgur.cloudshareapi.document.PaymentTransaction;
import in.bushansirgur.cloudshareapi.document.UserDocument;
import in.bushansirgur.cloudshareapi.dto.PaymentDTO;
import in.bushansirgur.cloudshareapi.dto.PaymentVerificationDTO;
import in.bushansirgur.cloudshareapi.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Formatter;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final UserService userService;
    private final UserCreditsService userCreditsService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;
    @Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    public PaymentDTO createOrder(PaymentDTO paymentDTO) {
        try {
            UserDocument currentUser = userService.getCurrentUser();
            String userId = currentUser.getId();

            if (isBlank(razorpayKeyId) || razorpayKeyId.contains("ADD_YOUR")
                    || isBlank(razorpayKeySecret) || razorpayKeySecret.contains("ADD_YOUR")) {
                log.error("Razorpay keys are not configured");
                return PaymentDTO.builder()
                        .success(false)
                        .message("Payment gateway is not configured on the server.")
                        .build();
            }

            Integer amount = paymentDTO.getAmount();
            if (amount == null || amount <= 0) {
                log.warn("Rejected order — invalid amount for userId={}", userId);
                return PaymentDTO.builder()
                        .success(false)
                        .message("Invalid amount. Amount must be greater than zero (in paise).")
                        .build();
            }
            String currency = isBlank(paymentDTO.getCurrency()) ? "INR" : paymentDTO.getCurrency();

            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", "order_" + System.currentTimeMillis());
            log.info("Creating Razorpay order: userId={}, planId={}, amount={} {}",
                    userId, paymentDTO.getPlanId(), amount, currency);

            Order order = razorpayClient.orders.create(orderRequest);
            String orderId = order.get("id");
            log.info("Razorpay order created: orderId={}", orderId);

            PaymentTransaction transaction = PaymentTransaction.builder()
                    .userId(userId)
                    .orderId(orderId)
                    .planId(paymentDTO.getPlanId())
                    .amount(paymentDTO.getAmount())
                    .currency(paymentDTO.getCurrency())
                    .status("PENDING")
                    .transactionDate(LocalDateTime.now())
                    .userEmail(currentUser.getEmail())
                    .userName(currentUser.getFirstName() + " " + currentUser.getLastName())
                    .build();

            paymentTransactionRepository.save(transaction);

            return PaymentDTO.builder()
                    .orderId(orderId)
                    .keyId(razorpayKeyId)
                    .success(true)
                    .message("Order created successfully")
                    .build();

        } catch (Exception e) {
            log.error("Razorpay order creation failed", e);
            return PaymentDTO.builder()
                    .success(false)
                    .message("Error creating order. Please try again.")
                    .build();
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public PaymentDTO verifyPayment(PaymentVerificationDTO request) {
        try {
            UserDocument currentUser = userService.getCurrentUser();
            String userId = currentUser.getId();
            String orderId = request.getRazorpay_order_id();

            log.info("Verifying payment: orderId={}, paymentId={}", orderId, request.getRazorpay_payment_id());

            Optional<PaymentTransaction> existing = paymentTransactionRepository.findByOrderId(orderId);
            if (existing.isPresent()) {
                PaymentTransaction tx = existing.get();
                if (!userId.equals(tx.getUserId())) {
                    log.warn("Payment verify rejected — order {} belongs to another user", orderId);
                    return PaymentDTO.builder()
                            .success(false)
                            .message("Payment verification failed")
                            .build();
                }
                if ("SUCCESS".equals(tx.getStatus())) {
                    log.info("Idempotent verify — order {} already processed", orderId);
                    return PaymentDTO.builder()
                            .success(true)
                            .message("Payment already verified")
                            .credits(userCreditsService.getUserCredits(userId).getCredits())
                            .build();
                }
            }

            String data = orderId + "|" + request.getRazorpay_payment_id();
            String generatedSignature = generateHmacSha256Signature(data, razorpayKeySecret);
            if (!generatedSignature.equals(request.getRazorpay_signature())) {
                log.warn("Signature mismatch for orderId={}", orderId);
                updateTransactionStatus(orderId, "FAILED", request.getRazorpay_payment_id(), null);
                return PaymentDTO.builder()
                        .success(false)
                        .message("Payment signature verification failed")
                        .build();
            }

            int creditsToAdd = 0;
            String plan = "BASIC";

            switch (request.getPlanId()) {
                case "premium":
                    creditsToAdd = 500;
                    plan = "PREMIUM";
                    break;
                case "ultimate":
                    creditsToAdd = 5000;
                    plan = "ULTIMATE";
                    break;
                default:
                    updateTransactionStatus(orderId, "FAILED", request.getRazorpay_payment_id(), null);
                    return PaymentDTO.builder()
                            .success(false)
                            .message("Invalid plan selected")
                            .build();
            }

            userCreditsService.addCredits(userId, creditsToAdd, plan);
            updateTransactionStatus(orderId, "SUCCESS", request.getRazorpay_payment_id(), creditsToAdd);
            return PaymentDTO.builder()
                    .success(true)
                    .message("Payment verified and credits added successfully")
                    .credits(userCreditsService.getUserCredits(userId).getCredits())
                    .build();

        } catch (Exception e) {
            try {
                updateTransactionStatus(request.getRazorpay_order_id(), "ERROR", request.getRazorpay_payment_id(), null);
            } catch (Exception ex) {
                log.error("Failed to update transaction after verify error", ex);
            }
            log.error("Payment verification failed", e);
            return PaymentDTO.builder()
                    .success(false)
                    .message("Error verifying payment. Please contact support.")
                    .build();
        }
    }

    private void updateTransactionStatus(String razorpayOrderId, String status, String razorpayPaymentId, Integer creditsToAdd) {
        paymentTransactionRepository.findByOrderId(razorpayOrderId)
                .ifPresent(transaction -> {
                    transaction.setStatus(status);
                    transaction.setPaymentId(razorpayPaymentId);
                    if (creditsToAdd != null) {
                        transaction.setCreditsAdded(creditsToAdd);
                    }
                    paymentTransactionRepository.save(transaction);
                });
    }

    private String generateHmacSha256Signature(String data, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);

        byte[] hmacData = mac.doFinal(data.getBytes());

        return toHexString(hmacData);
    }

    private String toHexString(byte[] bytes) {
        Formatter formatter = new Formatter();
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }
}
