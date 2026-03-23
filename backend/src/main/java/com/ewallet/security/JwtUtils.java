package com.ewallet.security;

import com.ewallet.config.MessageSourceConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static com.ewallet.common.MessageKeys.*;

/**
 * Utility class for JWT operations — uses jjwt 0.12.x API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final MessageSourceConfig messageConfig;

    @Value("${app.security.jwtSecret}")
    private String jwtSecret;

    @Value("${app.security.jwtExpirationMs}")
    private int jwtExpirationMs;

    /**
     * Derives an HMAC-SHA key from the configured secret string.
     * jjwt 0.12.x requires explicit SecretKey wrapping.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        ); // 0.12.x requires explicit key wrapping — raw strings no longer accepted
    }

    public String generateJwtToken(Authentication authentication) {
        final UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        return Jwts.builder()
                .subject(userPrincipal.getUsername()) // new builder uses .subject() not .setSubject()
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs)) // .expiration() replaces .setExpiration()
                .signWith(getSigningKey()) // algorithm is inferred from key type
                .compact();
    }

    public String getUsernameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey()) // replaces .setSigningKey()
                .build()
                .parseSignedClaims(token) // replaces .parseClaimsJws()
                .getPayload()
                .getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken); // throws on any validation failure
            return true;
        } catch (SecurityException e) {
            // SecurityException covers both SignatureException and key issues in 0.12.x
            log.error(messageConfig.getMessage(ERROR_JWT_INVALID_SIGNATURE, e.getMessage()));
        } catch (MalformedJwtException e) {
            log.error(messageConfig.getMessage(ERROR_JWT_INVALID_TOKEN, e.getMessage()));
        } catch (ExpiredJwtException e) {
            log.error(messageConfig.getMessage(ERROR_JWT_EXPIRED, e.getMessage()));
        } catch (UnsupportedJwtException e) {
            log.error(messageConfig.getMessage(ERROR_JWT_UNSUPPORTED, e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.error(messageConfig.getMessage(ERROR_JWT_EMPTY_CLAIMS, e.getMessage()));
        }
        return false;
    }
}
