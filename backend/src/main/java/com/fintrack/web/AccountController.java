package com.fintrack.web;

import com.fintrack.security.CurrentUser;
import com.fintrack.service.AccountService;
import com.fintrack.web.dto.UserDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService accounts;
    private final CurrentUser currentUser;

    public AccountController(AccountService accounts, CurrentUser currentUser) {
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return accounts.register(request);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return accounts.login(request, http.getRemoteAddr(), http.getHeader("User-Agent"));
    }

    /** Public, so locked-out users can reach an administrator. */
    @PostMapping("/auth/support")
    @ResponseStatus(HttpStatus.CREATED)
    public void requestSupport(@Valid @RequestBody SupportRequestCreate request, HttpServletRequest http) {
        accounts.requestSupport(request, http.getRemoteAddr());
    }

    @GetMapping("/me")
    public UserResponse me() {
        return accounts.me(currentUser.get());
    }

    @PutMapping("/me/savings")
    public UserResponse setSavings(@Valid @RequestBody SavingsRequest request) {
        return accounts.setSavingsPercent(currentUser.id(), request.percent());
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        accounts.changePassword(currentUser.id(), request);
    }

    @GetMapping("/me/change-requests")
    public List<ChangeRequestResponse> myRequests() {
        return accounts.myRequests(currentUser.id());
    }

    @PostMapping("/me/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeRequestResponse requestChange(@Valid @RequestBody ChangeRequestCreate request) {
        return accounts.requestChange(currentUser.id(), request);
    }
}
