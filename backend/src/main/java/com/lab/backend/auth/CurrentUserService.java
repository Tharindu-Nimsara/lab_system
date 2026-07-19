package com.lab.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository users;

    /** The authenticated user backing the current request's session. */
    public AppUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BadCredentialsException("Not authenticated");
        }
        return users.findByEmailAndIsActiveTrue(auth.getName())
                .orElseThrow(() -> new BadCredentialsException("Not authenticated"));
    }
}
