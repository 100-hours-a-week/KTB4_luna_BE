package com.example.community.global.dto;

import com.example.community.user.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthorDTO {
    private Long userId;
    private UserStatus status;
    private String nickname;
    private String profileImageUrl;
}
