package com.example.community.realtime.controller;

import com.example.community.global.auth.JwtTokenProvider;
import com.example.community.global.config.SecurityConfig;
import com.example.community.global.config.filter.JwtFilter;
import com.example.community.realtime.service.RealtimeStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RealtimeStreamController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class RealtimeStreamControllerTest {

    private static final long MAX_CONNECTION_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RealtimeStreamService realtimeStreamService;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    Authentication authentication;

    @BeforeEach
    void setUp() throws Exception {
        authentication = new UsernamePasswordAuthenticationToken(
                "1",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        when(realtimeStreamService.connect(eq(1L), any(SseEmitter.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    @DisplayName("인증 사용자는 SSE stream을 연다")
    void opensStreamForAuthenticatedUser() throws Exception {
        when(jwtTokenProvider.getRemainingValidityMillis("access-token")).thenReturn(10_000L);

        mockMvc.perform(get("/api/realtime/stream")
                        .with(authentication(authentication))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        ArgumentCaptor<SseEmitter> emitterCaptor = ArgumentCaptor.forClass(SseEmitter.class);
        verify(realtimeStreamService).connect(eq(1L), emitterCaptor.capture());
        assertThat(emitterCaptor.getValue().getTimeout()).isEqualTo(10_000L);
        emitterCaptor.getValue().complete();
    }

    @Test
    @DisplayName("SSE 연결시간은 서버 최대 연결시간을 넘지 않는다")
    void limitsStreamToServerMaximumTimeout() throws Exception {
        when(jwtTokenProvider.getRemainingValidityMillis("access-token"))
                .thenReturn(Duration.ofHours(1).toMillis());

        mockMvc.perform(get("/api/realtime/stream")
                        .with(authentication(authentication))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        ArgumentCaptor<SseEmitter> emitterCaptor = ArgumentCaptor.forClass(SseEmitter.class);
        verify(realtimeStreamService).connect(eq(1L), emitterCaptor.capture());
        assertThat(emitterCaptor.getValue().getTimeout()).isEqualTo(MAX_CONNECTION_TIMEOUT_MILLIS);
        emitterCaptor.getValue().complete();
    }

    @Test
    @DisplayName("남은 유효시간이 없는 Access Token은 SSE stream을 열 수 없다")
    void rejectsAccessTokenWithoutRemainingValidity() throws Exception {
        when(jwtTokenProvider.getRemainingValidityMillis("access-token")).thenReturn(0L);

        mockMvc.perform(get("/api/realtime/stream")
                        .with(authentication(authentication))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(realtimeStreamService);
    }

    @Test
    @DisplayName("Bearer 형식이 아닌 Authorization header는 거부한다")
    void rejectsMalformedAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/realtime/stream")
                        .with(authentication(authentication))
                        .header(HttpHeaders.AUTHORIZATION, "Basic access-token")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(realtimeStreamService);
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 SSE stream을 열 수 없다")
    void rejectsUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/realtime/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(realtimeStreamService);
    }
}
