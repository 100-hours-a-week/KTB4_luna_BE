package com.example.community.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CommentDTO {
    private Long commentId;
    private Long parentCommentId;
    private String commentBody;
    private LocalDateTime createdAt;
    private boolean modified;
    private LocalDateTime modifiedAt;

    private boolean deleted;
    private LocalDateTime deletedAt;
}
