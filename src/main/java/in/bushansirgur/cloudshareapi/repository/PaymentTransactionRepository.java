package in.bushansirgur.cloudshareapi.repository;

import in.bushansirgur.cloudshareapi.document.PaymentTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends MongoRepository<PaymentTransaction, String> {

    Optional<PaymentTransaction> findByOrderId(String orderId);

    List<PaymentTransaction> findByUserId(String userId);

    List<PaymentTransaction> findByUserIdOrderByTransactionDateDesc(String userId);

    List<PaymentTransaction> findByUserIdAndStatusOrderByTransactionDateDesc(String userId, String status);
}
