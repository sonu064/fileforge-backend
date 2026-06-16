package in.bushansirgur.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "email_verification_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    private String userId;

    private Instant expiresAt;
}
