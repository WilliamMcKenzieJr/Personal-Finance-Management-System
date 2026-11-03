package com.fintrack.security;

import com.fintrack.model.Enums.Role;
import com.fintrack.model.User;
import com.fintrack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the single Owner account on first start, from the app.owner.* properties. */
@Component
public class OwnerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    public OwnerBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
                          @Value("${app.owner.username}") String username,
                          @Value("${app.owner.email}") String email,
                          @Value("${app.owner.password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.OWNER)) {
            return;
        }
        User owner = new User();
        owner.setUsername(username);
        owner.setEmail(email);
        owner.setPasswordHash(passwordEncoder.encode(password));
        owner.setFirstName("Site");
        owner.setLastName("Owner");
        owner.setRole(Role.OWNER);
        users.save(owner);
        log.warn("Created owner account '{}'. Change its password after first sign-in.", username);
    }
}
