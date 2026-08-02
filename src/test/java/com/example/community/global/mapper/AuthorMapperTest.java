package com.example.community.global.mapper;

import com.example.community.global.dto.AuthorDTO;
import com.example.community.user.entity.User;
import com.example.community.user.entity.UserRole;
import com.example.community.user.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorMapperTest {

    private final AuthorMapper mapper = new AuthorMapper();

    @Test
    @DisplayName("활성 작성자 userId를 매핑한다")
    void mapsActiveUserId() {
        User user = new User(7L, "author", "", UserRole.ROLE_USER, UserStatus.ACTIVE);

        AuthorDTO result = mapper.toAuthorDTO(user);

        assertThat(result.getUserId()).isEqualTo(7L);
        assertThat(result.getNickname()).isEqualTo("author");
    }

    @Test
    @DisplayName("탈퇴 작성자도 실제 userId를 유지한다")
    void mapsWithdrawnUserId() {
        User user = new User(8L, "author", "", UserRole.ROLE_USER, UserStatus.WITHDRAWN);

        AuthorDTO result = mapper.toAuthorDTO(user);

        assertThat(result.getUserId()).isEqualTo(8L);
        assertThat(result.getNickname()).isEqualTo("알 수 없음");
    }
}
