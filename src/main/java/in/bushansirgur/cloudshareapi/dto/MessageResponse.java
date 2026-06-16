package in.bushansirgur.cloudshareapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageResponse {
    private String message;
    /** Dev-only convenience: the verification/reset token, exposed when app.auth.dev-expose-tokens=true. */
    private String devToken;
}
