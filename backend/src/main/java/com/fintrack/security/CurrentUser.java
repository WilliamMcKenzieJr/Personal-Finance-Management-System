package com.fintrack.security;

import com.fintrack.model.User;
import com.fintrack.repository.UserRepository;
import com.fintrack.service.AccountService;
import com.fintrack.web.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * The signed-in user. Financial data is always looked up by this id, which is what keeps
 * each member's records private to them.
 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Long id() {
        return get().getId();
    }

    /**
     * Loads the user from the database on every request, so a deleted or locked account loses
     * access straight away instead of when its sign-in token expires.
     */
    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw ApiException.unauthorized("Please sign in");
        }
        User user = users.findById(Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> ApiException.unauthorized("This account no longer exists"));
        if (user.isAccountLocked()) {
            throw AccountService.accountLocked();
        }
        return user;
    }
}
