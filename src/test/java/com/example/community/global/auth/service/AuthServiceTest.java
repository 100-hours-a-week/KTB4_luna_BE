package com.example.community.global.auth.service;

import com.example.community.global.auth.JwtToken;
import com.example.community.global.auth.JwtTokenProvider;
import com.example.community.global.auth.session.RefreshSession;
import com.example.community.global.auth.session.RefreshSessionStore;
import com.example.community.global.auth.session.RefreshTokenHasher;
import com.example.community.global.exceptions.UnauthorizedException;
import com.example.community.user.entity.User;
import com.example.community.user.entity.UserRole;
import com.example.community.user.entity.UserStatus;
import com.example.community.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    UserRepository userRepository;
    @Mock
    JwtTokenProvider jwtTokenProvider;
    @Mock
    RefreshSessionStore refreshSessionStore;
    @Mock
    RefreshTokenHasher refreshTokenHasher;

    @InjectMocks
    AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(1L, "tester", "", UserRole.ROLE_USER, UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("유효한 refresh token은 같은 sessionId로 rotation한다.")
    void refresh_rotatesSession(){
        String refreshToken = "refresh-token";
        String sessionId = "session-1";
        RefreshSession current = new RefreshSession(
                user.getUserId(),
                sessionId,
                "old-hash",
                Instant.now().plusSeconds(60)
        );
        JwtToken rotatedToken = new JwtToken("Bearer", "new-access", "new-refresh");

        when(jwtTokenProvider.validateRefreshToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(user.getUserId());
        when(jwtTokenProvider.getSessionId(refreshToken)).thenReturn(sessionId);
        when(refreshTokenHasher.hash(refreshToken)).thenReturn("old-hash");
        when(refreshSessionStore.findByUserId(user.getUserId())).thenReturn(Optional.of(current));
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createJwtToken(user, sessionId)).thenReturn(rotatedToken);
        when(jwtTokenProvider.getRemainingValidityMillis("new-refresh")).thenReturn(604800000L);
        when(refreshTokenHasher.hash("new-refresh")).thenReturn("new-hash");
        when(refreshSessionStore.rotateIfHashMatches(eq(user.getUserId()), eq("old-hash"), any(RefreshSession.class)))
                .thenReturn(true);

        assertThat(authService.refresh(refreshToken)).isEqualTo(rotatedToken);

        verify(refreshSessionStore).rotateIfHashMatches(
                eq(user.getUserId()),
                eq("old-hash"),
                argThat(replacement -> replacement.sessionId().equals(sessionId)
                        && replacement.refreshTokenHash().equals("new-hash"))
        );
    }

    @Test
    @DisplayName("access token은 refresh에 사용할 수 없다.")
    void refresh_rejectsNonRefreshToken(){
        String accessToken = "access-token";
        when(jwtTokenProvider.validateRefreshToken(accessToken)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(accessToken))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(refreshSessionStore, refreshTokenHasher, userRepository);
    }

    @Test
    @DisplayName("현재 session이 없으면 refresh할 수 없다.")
    void refresh_rejectsMissingSession(){
        String refreshToken = "refresh-token";
        when(jwtTokenProvider.validateRefreshToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(user.getUserId());
        when(jwtTokenProvider.getSessionId(refreshToken)).thenReturn("session-1");
        when(refreshTokenHasher.hash(refreshToken)).thenReturn("old-hash");
        when(refreshSessionStore.findByUserId(user.getUserId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(UnauthorizedException.class);
        verify(refreshSessionStore, never()).rotateIfHashMatches(anyLong(), anyString(), any(RefreshSession.class));
    }
}
