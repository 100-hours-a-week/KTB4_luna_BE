package com.example.community.comment.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommentRequestDTO {
    @NotBlank(message = "댓글 내용을 입력해주세요.")
    private String commentBody;

    @Nullable
    private Long parentCommentId;
}
