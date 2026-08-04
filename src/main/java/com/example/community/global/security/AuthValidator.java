package com.example.community.global.security;

import com.example.community.global.exceptions.ForbiddenException;
import com.example.community.global.exceptions.UnauthorizedException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@AllArgsConstructor
public class AuthValidator {
    public void validateOwner(Long ownerId, Long authorId) {
        if (!ownerId.equals(authorId)) throw new ForbiddenException();
    }
}
