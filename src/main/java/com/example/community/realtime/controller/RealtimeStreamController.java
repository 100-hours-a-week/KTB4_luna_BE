package com.example.community.realtime.controller;

import com.example.community.realtime.service.RealtimeStreamService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeStreamController {
    private final RealtimeStreamService streamService;

    public RealtimeStreamController(RealtimeStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) throws IOException{
        long userId = getLoginUserId(authentication);
        SseEmitter sseEmitter = new SseEmitter();
        return streamService.connect(userId, sseEmitter);
    }

    private long getLoginUserId(Authentication authentication){
        return Long.parseLong(authentication.getName());
    }

}
