package com.example.tribeo.security.jwt;

import com.example.tribeo.security.services.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import javax.crypto.SecretKey;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    @Value("${spring.app.jwtSecret}")
    private String jwtSecret;

    @Value("${spring.app.jwtExpirationMs}")
    private long jwtExpirationMs;

    @Value("${spring.app.jwtRefreshExpirationMs}")
    private long jwtRefreshExpirationMs;

    @Value("${spring.ecom.app.jwtCookieName}")
    private String jwtCookie;

    @Value("${spring.ecom.app.jwtRefreshCookieName}")
    private String jwtRefreshCookie;

    @Value("${spring.app.jwtCookieSecure:false}")
    private boolean jwtCookieSecure;

    @Value("${spring.app.jwtCookieSameSite:Lax}")
    private String jwtCookieSameSite;

    @Value("${spring.app.jwtCookieDomain:}")
    private String jwtCookieDomain;

    @PostConstruct
    void validateJwtSecret() {
        try {
            key();
        } catch (IllegalArgumentException | WeakKeyException e) {
            throw new IllegalStateException(
                    "Invalid spring.app.jwtSecret. Set SPRING_APP_JWT_SECRET to a Base64-encoded HMAC key, for example: openssl rand -base64 32",
                    e
            );
        }
    }

    public String getJwtFromCookies(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, jwtCookie);
        if (cookie != null) {
            return cookie.getValue();
        } else {
            return null;
        }
    }

    public String getRefreshJwtFromCookies(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, jwtRefreshCookie);
        if (cookie != null) {
            return cookie.getValue();
        } else {
            return null;
        }
    }

    public String getJwtFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public ResponseCookie generateJwtCookie(UserDetailsImpl userPrincipal) {
        String jwt = generateAccessTokenFromUsername(userPrincipal.getUsername());
        return buildCookie(jwtCookie, jwt, "/api", jwtExpirationMs);
    }

    public ResponseCookie generateRefreshJwtCookie(String refreshToken) {
        return buildCookie(jwtRefreshCookie, refreshToken, "/api/auth", jwtRefreshExpirationMs);
    }

    public ResponseCookie getCleanJwtCookie() {
        return buildCleanCookie(jwtCookie, "/api");
    }

    public ResponseCookie getCleanRefreshJwtCookie() {
        return buildCleanCookie(jwtRefreshCookie, "/api/auth");
    }

    private ResponseCookie buildCookie(String name, String value, String path, long maxAgeMs) {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(name, value)
                .path(path)
                .maxAge(Duration.ofMillis(maxAgeMs))
                .httpOnly(true)
                .secure(jwtCookieSecure)
                .sameSite(jwtCookieSameSite);

        if (StringUtils.hasText(jwtCookieDomain)) {
            cookieBuilder.domain(jwtCookieDomain);
        }

        return cookieBuilder.build();
    }

    private ResponseCookie buildCleanCookie(String name, String path) {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(name, "")
                .path(path)
                .maxAge(0)
                .httpOnly(true)
                .secure(jwtCookieSecure)
                .sameSite(jwtCookieSameSite);

        if (StringUtils.hasText(jwtCookieDomain)) {
            cookieBuilder.domain(jwtCookieDomain);
        }

        return cookieBuilder.build();
    }

    public String generateAccessTokenFromUsername(String username) {
        return generateTokenFromUsername(username, jwtExpirationMs, ACCESS_TOKEN_TYPE);
    }

    public String generateRefreshTokenFromUsername(String username) {
        return generateTokenFromUsername(username, jwtRefreshExpirationMs, REFRESH_TOKEN_TYPE);
    }

    private String generateTokenFromUsername(String username, long expirationMs, String tokenType) {
        return Jwts.builder()
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + expirationMs))
                .signWith(key())
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return parseClaims(token)
                .getPayload().getSubject();
    }

    public Instant getExpirationInstantFromJwtToken(String token) {
        return parseClaims(token)
                .getPayload().getExpiration().toInstant();
    }

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public boolean validateJwtToken(String authToken) {
        return validateToken(authToken, ACCESS_TOKEN_TYPE);
    }

    public boolean validateRefreshJwtToken(String authToken) {
        return validateToken(authToken, REFRESH_TOKEN_TYPE);
    }

    private boolean validateToken(String authToken, String expectedTokenType) {
        try {
            Claims claims = parseClaims(authToken).getPayload();
            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (!expectedTokenType.equals(tokenType)) {
                logger.error("JWT token has invalid token type: {}", tokenType);
                return false;
            }
            return true;
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    private Jws<Claims> parseClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) key())
                .build().parseSignedClaims(token);
    }
}
