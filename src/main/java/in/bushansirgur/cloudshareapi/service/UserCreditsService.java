package in.bushansirgur.cloudshareapi.service;

import in.bushansirgur.cloudshareapi.document.UserDocument;
import in.bushansirgur.cloudshareapi.exceptions.ResourceNotFoundException;
import in.bushansirgur.cloudshareapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Credits now live directly on the user document (the users collection). */
@Service
@RequiredArgsConstructor
public class UserCreditsService {

    private final UserRepository userRepository;
    private final UserService userService;

    public UserDocument getUserCredits() {
        return userService.getCurrentUser();
    }

    public UserDocument getUserCredits(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
    }

    public Boolean hasEnoughCredits(int requiredCredits) {
        UserDocument user = getUserCredits();
        return user.getCredits() != null && user.getCredits() >= requiredCredits;
    }

    public UserDocument consumeCredit() {
        UserDocument user = getUserCredits();
        int current = user.getCredits() == null ? 0 : user.getCredits();
        if (current <= 0) {
            return null;
        }
        user.setCredits(current - 1);
        return userRepository.save(user);
    }

    public UserDocument addCredits(String userId, Integer creditsToAdd, String plan) {
        UserDocument user = getUserCredits(userId);
        int current = user.getCredits() == null ? 0 : user.getCredits();
        user.setCredits(current + creditsToAdd);
        user.setPlan(plan);
        return userRepository.save(user);
    }
}
