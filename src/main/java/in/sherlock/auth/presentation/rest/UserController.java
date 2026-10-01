package in.sherlock.auth.presentation.rest;

import in.sherlock.auth.application.port.in.UserQueryUseCase;
import in.sherlock.auth.infrastructure.security.AuthPrincipal;
import in.sherlock.auth.presentation.rest.AuthDtos.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserQueryUseCase users;

    public UserController(UserQueryUseCase users) {
        this.users = users;
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return UserResponse.from(users.me(principal.userId()));
    }
}
