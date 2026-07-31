package com.example.community.realtime.event;

import lombok.AllArgsConstructor;

import java.time.Instant;

public record CommentCreatedEvent(String eventId, Long postId, Long commentId, Long parentCommentId, Long actorUserId, Instant occurredAt) {

}