package com.example.community.user.controller;

import com.example.community.global.ApiResponse;
import com.example.community.user.dto.*;
import com.example.community.user.entity.UserRole;
import com.example.community.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final String refreshCookieName;
    private final String refreshCookiePath;
    private final boolean refreshCookieSecure;
    private final String refreshCookieSameSite;
    private final long refreshTokenExpirationMs;

    public UserController(UserService userService,
                          @Value("${jwt.refresh-cookie-name}") String refreshCookieName,
                          @Value("${jwt.refresh-cookie-path}") String refreshCookiePath,
                          @Value("${jwt.refresh-cookie-secure}") boolean refreshCookieSecure,
                          @Value("${jwt.refresh-cookie-same-site}") String refreshCookieSameSite,
                          @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.userService = userService;
        this.refreshCookieName = refreshCookieName;
        this.refreshCookiePath = refreshCookiePath;
        this.refreshCookieSecure = refreshCookieSecure;
        this.refreshCookieSameSite = refreshCookieSameSite;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(@Valid @RequestBody LoginRequestDTO requestDTO) {
        LoginResponseDTO responseDTO = userService.login(requestDTO);
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
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(Authentication authentication) {
        Long loginUserId = getLoginUserId(authentication);
        userService.logout(loginUserId);
        return ResponseEntity.ok(new ApiResponse<>("logout_success", null));
    }
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponseDTO>> signUp(@Valid @RequestBody SignUpRequestDTO requestDTO) {
        SignUpResponseDTO responseDTO = userService.signUp(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>("user_register_success", responseDTO));
    }
    @PatchMapping("/{userId}/info")
    public ResponseEntity<ApiResponse<ModifyInfoResponseDTO>> modifyInfo(Authentication authentication, @PathVariable Long userId, @Valid @RequestBody ModifyInfoRequestDTO requestDTO){
        Long loginUserId = getLoginUserId(authentication);

        ModifyInfoResponseDTO responseDTO = userService.modifyInfo(loginUserId, userId, requestDTO);
        return ResponseEntity.ok().body(new ApiResponse<>("user_modify_success", responseDTO));
    }
    @PatchMapping("/{userId}/password")
    public ResponseEntity<ApiResponse<String>> modifyPassword(Authentication authentication, @PathVariable Long userId, @Valid @RequestBody ModifyPasswordRequestDTO requestDTO){
        Long loginUserId = getLoginUserId(authentication);
        userService.modifyPassword(loginUserId, userId, requestDTO);
        return ResponseEntity.ok().body(new ApiResponse<>("password_modify_success", null));
    }
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<WithdrawResponseDTO>>  withdraw(Authentication authentication, @PathVariable Long userId){
        Long loginUserId = getLoginUserId(authentication);
        WithdrawResponseDTO responseDTO = userService.withdraw(loginUserId, userId);
        return ResponseEntity.ok().body(new ApiResponse<>("user_withdraw_success", responseDTO));
    }

    private Long getLoginUserId(Authentication authentication){
        return Long.valueOf(authentication.getName());
    }

}
