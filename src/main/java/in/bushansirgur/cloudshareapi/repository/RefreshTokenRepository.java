package in.bushansirgur.cloudshareapi.repository;

import in.bushansirgur.cloudshareapi.document.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUserId(String userId);
}
