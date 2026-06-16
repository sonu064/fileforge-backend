package in.bushansirgur.cloudshareapi.repository;

import in.bushansirgur.cloudshareapi.document.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUserId(String userId);
}
