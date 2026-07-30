package com.example.community.realtime.controller;

import com.example.community.global.auth.JwtTokenProvider;
import com.example.community.realtime.service.RealtimeStreamService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeStreamController {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final long MAX_CONNECTION_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private final RealtimeStreamService streamService;
    private final JwtTokenProvider jwtTokenProvider;

    public RealtimeStreamController(RealtimeStreamService streamService, JwtTokenProvider jwtTokenProvider) {
        this.streamService = streamService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication, @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) throws IOException {
        long userId = getLoginUserId(authentication);
        long timeoutMillis = getTimeoutMillis(authorization);
        SseEmitter sseEmitter = new SseEmitter(timeoutMillis);
        return streamService.connect(userId, sseEmitter);
    }

    private long getLoginUserId(Authentication authentication){
        return Long.parseLong(authentication.getName());
    }

    private long getTimeoutMillis(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX) || authorization.length() == BEARER_PREFIX.length()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String accessToken = authorization.substring(BEARER_PREFIX.length());
        long remainingMillis = jwtTokenProvider.getRemainingValidityMillis(accessToken);
        if (remainingMillis <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return Math.min(MAX_CONNECTION_TIMEOUT_MILLIS, remainingMillis);
    }
}
