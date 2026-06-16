package in.bushansirgur.cloudshareapi.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/** Convenience accessor for the authenticated user's id (the JWT subject = Mongo user id). */
@Component
public class SecurityUtil {

    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UsernameNotFoundException("User not authenticated");
        }
        return authentication.getName();
    }
}
