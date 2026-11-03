package com.fintrack.service;

import com.fintrack.model.Enums.AuditAction;
import com.fintrack.model.Enums.LoginOutcome;
import com.fintrack.model.Enums.RequestStatus;
import com.fintrack.model.Enums.Role;
import com.fintrack.model.LoginAttempt;
import com.fintrack.model.ProfileChangeRequest;
import com.fintrack.model.SupportRequest;
import com.fintrack.model.User;
import com.fintrack.repository.LoginAttemptRepository;
import com.fintrack.repository.ProfileChangeRequestRepository;
import com.fintrack.repository.SupportRequestRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.security.JwtService;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.UserDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Registration, sign-in (with lockouts) and a user's own profile settings. */
@Service
public class AccountService {

    public static final String TEMPORARILY_LOCKED = "TEMPORARILY_LOCKED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";

    private static final HttpStatus LOCKED = HttpStatus.LOCKED;

    private final UserRepository users;
    private final ProfileChangeRequestRepository changeRequests;
    private final LoginAttemptRepository loginAttempts;
    private final SupportRequestRepository supportRequests;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final int maxAttempts;
    private final Duration lockDuration;

    public AccountService(UserRepository users, ProfileChangeRequestRepository changeRequests,
                          LoginAttemptRepository loginAttempts, SupportRequestRepository supportRequests,
                          PasswordEncoder passwordEncoder, JwtService jwtService, AuditService audit,
                          @Value("${app.security.max-login-attempts}") int maxAttempts,
                          @Value("${app.security.lockout-minutes}") long lockoutMinutes) {
        this.users = users;
        this.changeRequests = changeRequests;
        this.loginAttempts = loginAttempts;
        this.supportRequests = supportRequests;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockoutMinutes);
    }

    @Transactional
    public AuthResponse register(RegisterRequest r) {
        if (users.existsByUsernameIgnoreCase(r.username())) {
            throw ApiException.conflict("That username is taken.");
        }
        if (users.existsByEmailIgnoreCase(r.email())) {
            throw ApiException.conflict("An account with that email already exists.");
        }
        User user = new User();
        user.setUsername(r.username().trim());
        user.setEmail(r.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(r.password()));
        user.setFirstName(r.firstName().trim());
        user.setLastName(r.lastName().trim());
        user.setRole(Role.MEMBER);
        users.save(user);
        audit.record(user, user, AuditAction.ACCOUNT_CREATED, "Registered with email " + user.getEmail());
        return new AuthResponse(jwtService.issue(user), UserResponse.from(user));
    }

    /**
     * Signs in, enforcing lockouts: after {@code maxAttempts} wrong passwords the account is locked
     * for {@code lockDuration}; if the next attempt after that also fails, the account stays locked
     * until an administrator unlocks it. Every attempt is logged with the caller's IP address.
     *
     * <p>Deliberately not one transaction: the failure counters and the log entry must be saved
     * even though the call then fails.
     */
    public AuthResponse login(LoginRequest r, String ip, String userAgent) {
        String entered = r.username().trim();
        Optional<User> found = users.findByUsernameIgnoreCase(entered);
        if (found.isEmpty()) {
            logAttempt(entered, null, ip, userAgent, LoginOutcome.UNKNOWN_USER, null);
            throw ApiException.unauthorized("Incorrect username or password.");
        }
        User user = found.get();
        Instant now = Instant.now();

        // Attempts on an account that is already locked are refused without checking the password
        // and are not logged, so hammering a locked account can't flood the sign-in log.
        if (user.isAccountLocked()) {
            throw accountLocked();
        }
        if (user.getLockedUntil() != null && now.isBefore(user.getLockedUntil())) {
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toSeconds() / 60 + 1);
            throw new ApiException(LOCKED, "Your account is temporarily locked. Try again in " + minutes
                    + " minute" + (minutes == 1 ? "" : "s") + ".", TEMPORARILY_LOCKED);
        }

        if (!passwordEncoder.matches(r.password(), user.getPasswordHash())) {
            ApiException error;
            String detail;
            if (user.isTemporaryLockUsed()) {
                // First failure after a 10-minute lock has run out.
                if (user.getRole() == Role.OWNER) {
                    // Nobody outranks the owner to unlock it, so the owner only ever gets temporary locks.
                    user.setLockedUntil(now.plus(lockDuration));
                    detail = "Failed again after a temporary lock: owner locked for another "
                            + lockDuration.toMinutes() + " min";
                    error = temporaryLockStarted();
                } else {
                    user.setAccountLocked(true);
                    user.setLockedUntil(null);
                    detail = "Failed again after a temporary lock: account locked";
                    audit.record(null, user, AuditAction.ACCOUNT_LOCKED,
                            "Locked automatically after repeated failed sign-ins (last from " + ip + ")");
                    error = accountLocked();
                }
            } else {
                int failed = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(failed);
                if (failed >= maxAttempts) {
                    user.setFailedLoginAttempts(0);
                    user.setLockedUntil(now.plus(lockDuration));
                    user.setTemporaryLockUsed(true);
                    detail = "Attempt " + failed + " of " + maxAttempts + ": locked for "
                            + lockDuration.toMinutes() + " min";
                    error = temporaryLockStarted();
                } else {
                    int left = maxAttempts - failed;
                    detail = "Attempt " + failed + " of " + maxAttempts;
                    error = ApiException.unauthorized("Incorrect username or password. " + left + " attempt"
                            + (left == 1 ? "" : "s") + " left before your account is locked for "
                            + lockDuration.toMinutes() + " minutes.");
                }
            }
            users.save(user);
            logAttempt(entered, user.getId(), ip, userAgent, LoginOutcome.WRONG_PASSWORD, detail);
            throw error;
        }

        user.clearLoginFailures();
        users.save(user);
        logAttempt(entered, user.getId(), ip, userAgent, LoginOutcome.SUCCESS, null);
        return new AuthResponse(jwtService.issue(user), UserResponse.from(user));
    }

    /** Public: anyone (typically a locked-out user) can ask the administrators for help. */
    @Transactional
    public void requestSupport(SupportRequestCreate r, String ip) {
        if (supportRequests.countByIpAddressAndCreatedAtAfter(ip, Instant.now().minus(Duration.ofHours(1))) >= 3) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "You've sent several requests recently. Please wait before sending another.");
        }
        SupportRequest request = new SupportRequest();
        request.setUsername(r.username().trim());
        request.setEmail(r.email().trim().toLowerCase());
        request.setMessage(r.message().trim());
        request.setIpAddress(ip);
        supportRequests.save(request);
    }

    public UserResponse me(User user) {
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setSavingsPercent(Long userId, BigDecimal percent) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        AuditService.Changes changes = new AuditService.Changes()
                .add("savings %", user.getSavingsPercent().stripTrailingZeros().toPlainString(),
                        percent.stripTrailingZeros().toPlainString());
        user.setSavingsPercent(percent);
        if (!changes.isEmpty()) {
            audit.record(user, user, AuditAction.SAVINGS_CHANGED, changes.toString());
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest r) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(r.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(r.newPassword()));
        audit.record(user, user, AuditAction.PASSWORD_CHANGED, null);
    }

    @Transactional
    public ChangeRequestResponse requestChange(Long userId, ChangeRequestCreate r) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        String firstName = blankToNull(r.firstName());
        String lastName = blankToNull(r.lastName());
        String email = blankToNull(r.email());
        if (firstName == null && lastName == null && email == null) {
            throw ApiException.badRequest("Enter at least one change.");
        }
        if (changeRequests.existsByUserIdAndStatus(userId, RequestStatus.PENDING)) {
            throw ApiException.conflict("You already have a pending request. Wait for it to be reviewed.");
        }
        ProfileChangeRequest request = new ProfileChangeRequest();
        request.setUser(user);
        request.setFirstName(firstName);
        request.setLastName(lastName);
        request.setEmail(email == null ? null : email.toLowerCase());
        request.setNote(blankToNull(r.note()));
        changeRequests.save(request);
        audit.record(user, user, AuditAction.CHANGE_REQUESTED, requestedChanges(user, request).toString());
        return ChangeRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestResponse> myRequests(Long userId) {
        return changeRequests.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ChangeRequestResponse::from).toList();
    }

    static AuditService.Changes requestedChanges(User user, ProfileChangeRequest r) {
        AuditService.Changes changes = new AuditService.Changes();
        if (r.getFirstName() != null) changes.add("first name", user.getFirstName(), r.getFirstName());
        if (r.getLastName() != null) changes.add("last name", user.getLastName(), r.getLastName());
        if (r.getEmail() != null) changes.add("email", user.getEmail(), r.getEmail());
        return changes;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private ApiException temporaryLockStarted() {
        return new ApiException(LOCKED, "Too many failed attempts. Your account is locked for "
                + lockDuration.toMinutes() + " minutes. If the next sign-in after that also fails, "
                + "the account will stay locked until an administrator unlocks it.", TEMPORARILY_LOCKED);
    }

    /** Also used for open sessions of an account that gets locked. */
    public static ApiException accountLocked() {
        return new ApiException(LOCKED, "Your account is locked. "
                + "Send a support request and an administrator will unlock it.", ACCOUNT_LOCKED);
    }

    private void logAttempt(String entered, Long userId, String ip, String userAgent, LoginOutcome outcome,
                            String detail) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUsernameEntered(entered.length() > 100 ? entered.substring(0, 100) : entered);
        attempt.setUserId(userId);
        attempt.setIpAddress(ip);
        attempt.setUserAgent(userAgent == null || userAgent.length() <= 255 ? userAgent : userAgent.substring(0, 255));
        attempt.setOutcome(outcome);
        attempt.setDetail(detail);
        loginAttempts.save(attempt);
    }
}
