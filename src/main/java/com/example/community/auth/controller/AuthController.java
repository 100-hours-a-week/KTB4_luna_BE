package com.example.community.auth.controller;

import com.example.community.auth.dto.LoginRequestDTO;
import com.example.community.auth.dto.LoginResponseDTO;
import com.example.community.auth.service.AuthService;
import com.example.community.global.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final String refreshCookieName;
    private final String refreshCookiePath;
    private final boolean refreshCookieSecure;
    private final String refreshCookieSameSite;
    private final long refreshTokenExpirationMs;

    public AuthController(AuthService authService,
                           @Value("${jwt.refresh-cookie-name}") String refreshCookieName,
                           @Value("${jwt.refresh-cookie-path}") String refreshCookiePath,
                           @Value("${jwt.refresh-cookie-secure}") boolean refreshCookieSecure,
                           @Value("${jwt.refresh-cookie-same-site}") String refreshCookieSameSite,
                           @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs){
        this.authService = authService;
        this.refreshCookieName = refreshCookieName;
        this.refreshCookiePath = refreshCookiePath;
        this.refreshCookieSecure = refreshCookieSecure;
        this.refreshCookieSameSite = refreshCookieSameSite;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(@Valid @RequestBody LoginRequestDTO requestDTO) {
        LoginResponseDTO responseDTO = authService.login(requestDTO);
        ResponseCookie refreshCookie = ResponseCookie.from(refreshCookieName, responseDTO.getToken().getRefreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(refreshCookiePath)
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new ApiResponse<>("user_login_success", responseDTO));
    }
}
