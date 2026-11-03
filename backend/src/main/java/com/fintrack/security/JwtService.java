package com.fintrack.security;

import com.fintrack.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Duration expiry;

    public JwtService(JwtEncoder encoder, @Value("${app.jwt.expiry-hours}") long expiryHours) {
        this.encoder = encoder;
        this.expiry = Duration.ofHours(expiryHours);
    }

    /** Token whose subject is the user id, carrying the role for URL-level authorization. */
    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("fintrack")
                .issuedAt(now)
                .expiresAt(now.plus(expiry))
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
