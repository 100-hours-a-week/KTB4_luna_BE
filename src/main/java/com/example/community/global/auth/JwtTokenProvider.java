package com.example.community.global.auth;

import com.example.community.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;


@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey,
                            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
                            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs){

        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public JwtToken createJwtToken(User user){
        String accessToken = createAccessToken(user);
        String refreshToken = createRefreshToken(user);

        return JwtToken.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
    public String createAccessToken(User user){
        return createToken(user, accessTokenExpirationMs);
    }
    public String createRefreshToken(User user){
        return createToken(user, refreshTokenExpirationMs);
    }
    public Long getUserId(String token){
        return Long.valueOf(parseClaims(token).getSubject());
    }
    public String getRole(String token){
        return parseClaims(token).get("role", String.class);
    }
    public boolean validateToken(String token){
        try{
            parseClaims(token);
            return true;
        } catch(Exception e){
            return false;
        }
    }

    public Authentication getAuthentication(String token){
        long userId = getUserId(token);
        String role = getRole(token);

        return new UsernamePasswordAuthenticationToken(
                String.valueOf(userId),
                null,
                List.of(new SimpleGrantedAuthority(role))
        );
    }

    private String createToken(User user, long expirationMs) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
    private Claims parseClaims(String token){
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    public long getRemainingValidityMillis(String token){
        try {
            long remainingMillis =
                    parseClaims(token).getExpiration().getTime()
                            - System.currentTimeMillis();

            return Math.max(remainingMillis, 0L);
        } catch (ExpiredJwtException exception) {
            return 0L;
        }
    }
}
