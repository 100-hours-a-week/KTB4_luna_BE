package com.example.community.realtime.dto;

import com.example.community.realtime.connection.RealtimeInterestType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RealtimeInterestRequestDTO {
    @NotNull
    private RealtimeInterestType type;

    private Long postId;

    @NotNull
    private Long revision;
}
