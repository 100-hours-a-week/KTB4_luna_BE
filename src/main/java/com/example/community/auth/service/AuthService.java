package com.example.community.auth.service;

import com.example.community.global.security.jwt.JwtToken;
import com.example.community.global.security.jwt.JwtTokenProvider;
import com.example.community.auth.session.RefreshSession;
import com.example.community.auth.session.RefreshSessionStore;
import com.example.community.auth.session.RefreshTokenHasher;
import com.example.community.global.exceptions.UnauthorizedException;
import com.example.community.user.entity.User;
import com.example.community.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshSessionStore refreshSessionStore;
    private final RefreshTokenHasher refreshTokenHasher;

    public AuthService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider, RefreshSessionStore refreshSessionStore, RefreshTokenHasher refreshTokenHasher) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshSessionStore = refreshSessionStore;
        this.refreshTokenHasher = refreshTokenHasher;
    }
    public JwtToken refresh(String refreshToken){
        if (refreshToken == null || refreshToken.isBlank() || !jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new UnauthorizedException();
        }

        Long userId;
        String sessionId;

        try {
            userId = jwtTokenProvider.getUserId(refreshToken);
            sessionId = jwtTokenProvider.getSessionId(refreshToken);
        } catch (RuntimeException exception) {
            throw new UnauthorizedException();
        }

        if (sessionId == null || sessionId.isBlank()) throw new UnauthorizedException();

        String currentHash = refreshTokenHasher.hash(refreshToken);
        RefreshSession current = refreshSessionStore.findByUserId(userId)
                .filter(session -> session.userId() == userId
                        && session.sessionId().equals(sessionId)
                        && session.expiresAt().isAfter(Instant.now())
                        && sameHash(session.refreshTokenHash(), currentHash))
                .orElseThrow(UnauthorizedException::new);

        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(UnauthorizedException::new);

        JwtToken rotatedToken = jwtTokenProvider.createJwtToken(user, current.sessionId());
        long remainingValidityMillis = jwtTokenProvider.getRemainingValidityMillis(rotatedToken.getRefreshToken());
        if (remainingValidityMillis <= 0) throw new UnauthorizedException();

        Instant now = Instant.now();
        Instant replacementExpiry = now.plusMillis(remainingValidityMillis);
        if (replacementExpiry.isAfter(current.expiresAt())) {
            replacementExpiry = current.expiresAt();
        }
        RefreshSession replacement = new RefreshSession(
                userId,
                current.sessionId(),
                refreshTokenHasher.hash(rotatedToken.getRefreshToken()),
                replacementExpiry
        );

        if (!refreshSessionStore.rotateIfHashMatches(userId, currentHash, replacement)) throw new UnauthorizedException();

        return rotatedToken;
    }

    private boolean sameHash(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
