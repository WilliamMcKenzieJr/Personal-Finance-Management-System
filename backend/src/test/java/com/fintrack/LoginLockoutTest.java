package com.fintrack;

import com.fintrack.model.Enums.AuditAction;
import com.fintrack.model.Enums.LoginOutcome;
import com.fintrack.model.Enums.Role;
import com.fintrack.model.User;
import com.fintrack.repository.AuditLogRepository;
import com.fintrack.repository.LoginAttemptRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.security.CurrentUser;
import com.fintrack.service.AccountService;
import com.fintrack.service.AdminService;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.UserDtos.LoginRequest;
import com.fintrack.web.dto.UserDtos.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LoginLockoutTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final String IP = "203.0.113.7";

    @Autowired
    AccountService accounts;

    @Autowired
    AdminService admin;

    @Autowired
    UserRepository users;

    @Autowired
    LoginAttemptRepository loginAttempts;

    @Autowired
    AuditLogRepository auditLog;

    @Autowired
    CurrentUser currentUser;

    String username;

    @BeforeEach
    void newUser() {
        username = register();
    }

    private String register() {
        String name = "lockuser" + COUNTER.incrementAndGet();
        accounts.register(new RegisterRequest(name, name + "@test.local", "password123", "F", "L"));
        return name;
    }

    private void login(String password) {
        accounts.login(new LoginRequest(username, password), IP, "JUnit");
    }

    private String codeOf(String password) {
        try {
            login(password);
            return "OK";
        } catch (ApiException e) {
            return e.getCode() == null ? e.getStatus().name() : e.getCode();
        }
    }

    private void expireTemporaryLock() {
        User user = users.findByUsernameIgnoreCase(username).orElseThrow();
        user.setLockedUntil(Instant.now().minusSeconds(1));
        users.save(user);
    }

    @Test
    void fourWrongPasswordsStartATenMinuteLock() {
        assertThat(codeOf("wrong")).isEqualTo("UNAUTHORIZED");
        assertThat(codeOf("wrong")).isEqualTo("UNAUTHORIZED");
        assertThat(codeOf("wrong")).isEqualTo("UNAUTHORIZED");
        assertThat(codeOf("wrong")).isEqualTo(AccountService.TEMPORARILY_LOCKED);

        // Even the right password is refused during the lock.
        assertThat(codeOf("password123")).isEqualTo(AccountService.TEMPORARILY_LOCKED);
    }

    @Test
    void failingAgainAfterTheLockLocksTheAccount() {
        for (int i = 0; i < 4; i++) codeOf("wrong");
        expireTemporaryLock();

        assertThat(codeOf("wrong")).isEqualTo(AccountService.ACCOUNT_LOCKED);
        assertThat(codeOf("password123")).isEqualTo(AccountService.ACCOUNT_LOCKED);
        Long id = users.findByUsernameIgnoreCase(username).orElseThrow().getId();
        assertThat(auditLog.findByTargetUserIdOrderByCreatedAtDescIdDesc(id))
                .anyMatch(a -> a.getAction() == AuditAction.ACCOUNT_LOCKED);
    }

    @Test
    void succeedingAfterTheLockClearsIt() {
        for (int i = 0; i < 4; i++) codeOf("wrong");
        expireTemporaryLock();

        assertThat(codeOf("password123")).isEqualTo("OK");
        // A fresh set of attempts is available again.
        assertThat(codeOf("wrong")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void successfulSignInResetsTheCounter() {
        codeOf("wrong");
        codeOf("wrong");
        codeOf("wrong");
        assertThat(codeOf("password123")).isEqualTo("OK");
        assertThat(codeOf("wrong")).isEqualTo("UNAUTHORIZED");
        assertThat(codeOf("wrong")).isEqualTo("UNAUTHORIZED");
        assertThat(codeOf("wrong")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void adminCanUnlockAMemberAndItIsAudited() {
        for (int i = 0; i < 4; i++) codeOf("wrong");
        expireTemporaryLock();
        codeOf("wrong");

        User member = users.findByUsernameIgnoreCase(username).orElseThrow();
        User adminUser = users.findByUsernameIgnoreCase(register()).orElseThrow();
        adminUser.setRole(Role.ADMIN);
        users.save(adminUser);

        assertThat(admin.unlock(adminUser, member.getId()).lockStatus()).isEqualTo("NONE");
        assertThat(codeOf("password123")).isEqualTo("OK");
        assertThat(auditLog.findByTargetUserIdOrderByCreatedAtDescIdDesc(member.getId()))
                .anyMatch(a -> a.getAction() == AuditAction.ACCOUNT_UNLOCKED
                        && adminUser.getUsername().equals(a.getActorUsername()));
    }

    @Test
    void adminCannotUnlockAnotherAdmin() {
        User target = users.findByUsernameIgnoreCase(username).orElseThrow();
        target.setRole(Role.ADMIN);
        target.setAccountLocked(true);
        users.save(target);
        User otherAdmin = users.findByUsernameIgnoreCase(register()).orElseThrow();
        otherAdmin.setRole(Role.ADMIN);
        users.save(otherAdmin);

        assertThatThrownBy(() -> admin.unlock(otherAdmin, target.getId())).isInstanceOf(ApiException.class);
        User owner = users.findByUsernameIgnoreCase("owner").orElseThrow();
        assertThat(admin.unlock(owner, target.getId()).lockStatus()).isEqualTo("NONE");
    }

    @Test
    void ownerIsNeverPermanentlyLocked() {
        User owner = users.findByUsernameIgnoreCase("owner").orElseThrow();
        owner.setTemporaryLockUsed(true);
        users.save(owner);

        assertThatThrownBy(() -> accounts.login(new LoginRequest("owner", "wrong"), IP, "JUnit"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(AccountService.TEMPORARILY_LOCKED);
        assertThat(users.findByUsernameIgnoreCase("owner").orElseThrow().isAccountLocked()).isFalse();

        owner = users.findByUsernameIgnoreCase("owner").orElseThrow();
        owner.clearLoginFailures();
        users.save(owner);
    }

    private long loggedAttempts() {
        return loginAttempts.findAll().stream().filter(a -> a.getUsernameEntered().equals(username)).count();
    }

    private User userWithRole(Role role) {
        User u = users.findByUsernameIgnoreCase(register()).orElseThrow();
        u.setRole(role);
        return users.save(u);
    }

    @Test
    void attemptsOnALockedAccountAreNotLogged() {
        for (int i = 0; i < 4; i++) codeOf("wrong");
        long afterLock = loggedAttempts();
        assertThat(afterLock).isEqualTo(4);

        codeOf("wrong");
        codeOf("password123");
        assertThat(loggedAttempts()).isEqualTo(afterLock);

        expireTemporaryLock();
        codeOf("wrong"); // logged: this failure is what locks the account
        long afterFullLock = loggedAttempts();
        codeOf("wrong");
        codeOf("password123");
        assertThat(loggedAttempts()).isEqualTo(afterFullLock);
    }

    @Test
    void adminCanLockAMemberButNotAnAdminOrTheOwner() {
        User member = users.findByUsernameIgnoreCase(username).orElseThrow();
        User adminUser = userWithRole(Role.ADMIN);
        User otherAdmin = userWithRole(Role.ADMIN);
        User owner = users.findByUsernameIgnoreCase("owner").orElseThrow();

        assertThat(admin.lock(adminUser, member.getId(), "testing").lockStatus()).isEqualTo("LOCKED");
        assertThat(codeOf("password123")).isEqualTo(AccountService.ACCOUNT_LOCKED);
        assertThat(auditLog.findByTargetUserIdOrderByCreatedAtDescIdDesc(member.getId()))
                .anyMatch(a -> a.getAction() == AuditAction.ACCOUNT_LOCKED && a.getDetails().contains("testing"));

        assertThatThrownBy(() -> admin.lock(adminUser, otherAdmin.getId(), null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> admin.lock(adminUser, owner.getId(), null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> admin.lock(adminUser, adminUser.getId(), null)).isInstanceOf(ApiException.class);
    }

    @Test
    void ownerCanLockAdminsAndMembersButNotThemselves() {
        User owner = users.findByUsernameIgnoreCase("owner").orElseThrow();
        User member = users.findByUsernameIgnoreCase(username).orElseThrow();
        User adminUser = userWithRole(Role.ADMIN);

        assertThat(admin.lock(owner, adminUser.getId(), null).lockStatus()).isEqualTo("LOCKED");
        assertThat(admin.lock(owner, member.getId(), null).lockStatus()).isEqualTo("LOCKED");
        assertThatThrownBy(() -> admin.lock(owner, owner.getId(), null)).isInstanceOf(ApiException.class);

        assertThat(admin.unlock(owner, adminUser.getId()).lockStatus()).isEqualTo("NONE");
    }

    @Test
    void lockingEndsSessionsThatAreAlreadySignedIn() {
        User member = users.findByUsernameIgnoreCase(username).orElseThrow();
        Jwt token = Jwt.withTokenValue("t").header("alg", "HS256").subject(member.getId().toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
        try {
            assertThat(currentUser.id()).isEqualTo(member.getId());

            admin.lock(users.findByUsernameIgnoreCase("owner").orElseThrow(), member.getId(), null);

            assertThatThrownBy(() -> currentUser.id())
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getCode())
                    .isEqualTo(AccountService.ACCOUNT_LOCKED);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void everyAttemptIsLoggedWithTheIpAddress() {
        codeOf("wrong");
        codeOf("password123");
        accounts.login(new LoginRequest(username, "password123"), "198.51.100.4", "JUnit");
        assertThatThrownBy(() -> accounts.login(new LoginRequest("nobody-" + username, "x"), IP, "JUnit"))
                .isInstanceOf(ApiException.class);

        var mine = loginAttempts.findTop500ByUsernameEnteredContainingIgnoreCaseOrderByCreatedAtDescIdDesc(username);
        assertThat(mine).extracting(a -> a.getOutcome()).contains(
                LoginOutcome.WRONG_PASSWORD, LoginOutcome.SUCCESS, LoginOutcome.UNKNOWN_USER);
        assertThat(mine).extracting(a -> a.getIpAddress()).contains(IP, "198.51.100.4");
    }
}
