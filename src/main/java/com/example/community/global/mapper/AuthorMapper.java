package com.example.community.global.mapper;

import com.example.community.global.dto.AuthorDTO;
import com.example.community.user.entity.User;
import com.example.community.user.entity.UserStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthorMapper {
    public AuthorDTO toAuthorDTO(User author){
        if (UserStatus.WITHDRAWN.equals(author.getStatus())) {
            return new AuthorDTO(
                    author.getUserId(),
                    author.getStatus(),
                    "알 수 없음",
                    null
            );
        }
        return new AuthorDTO(
                author.getUserId(),
                author.getStatus(),
                author.getNickname(),
                author.getProfileImageUrl()
        );
    }
}
