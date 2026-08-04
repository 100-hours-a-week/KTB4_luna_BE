package com.example.community.auth.controller;

import com.example.community.global.auth.JwtToken;
import com.example.community.auth.service.AuthService;
import com.example.community.global.exceptions.GlobalExceptionHandler;
import com.example.community.global.exceptions.UnauthorizedException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "jwt.refresh-cookie-name=refresh_token",
        "jwt.refresh-cookie-path=/api/auth",
        "jwt.refresh-cookie-secure=true",
        "jwt.refresh-cookie-same-site=Lax",
        "jwt.refresh-token-expiration-ms=604800000"
})
class AuthControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @Test
    @DisplayName("refresh는 access token 없이 refresh cookie만으로 새 access token을 반환한다.")
    void refresh_withoutAccessToken_returnsAccessToken() throws Exception {
        JwtToken rotatedToken = new JwtToken("Bearer", "new-access-token", "new-refresh-token");
        when(authService.refresh("refresh-token")).thenReturn(rotatedToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("token_refresh_success"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refresh_token=new-refresh-token"),
                                containsString("Max-Age=604800"),
                                containsString("Path=/api/auth"),
                                containsString("HttpOnly"),
                                containsString("SameSite=Lax"),
                                containsString("Secure")
                        )));
        verify(authService).refresh("refresh-token");
    }

    @Test
    @DisplayName("refresh cookie가 없으면 401과 만료 cookie를 반환한다.")
    void refresh_withoutCookie_returns401AndExpiresCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("refresh_token_missing"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refresh_token="),
                                containsString("Max-Age=0"),
                                containsString("Path=/api/auth"),
                                containsString("HttpOnly"),
                                containsString("SameSite=Lax"),
                                containsString("Secure")
                        )));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("AuthService 검증 실패는 401과 refresh_token_invalid를 반환한다.")
    void refresh_invalidToken_returns401AndExpiresCookie() throws Exception {
        when(authService.refresh("invalid-refresh-token")).thenThrow(new UnauthorizedException());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "invalid-refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("refresh_token_invalid"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refresh_token="),
                                containsString("Max-Age=0"),
                                containsString("Path=/api/auth"),
                                containsString("HttpOnly"),
                                containsString("SameSite=Lax"),
                                containsString("Secure")
                        )));

        verify(authService).refresh("invalid-refresh-token");
    }
}
