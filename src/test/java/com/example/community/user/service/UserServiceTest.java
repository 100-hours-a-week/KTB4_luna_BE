package com.example.community.user.service;

import com.example.community.global.auth.*;
import com.example.community.auth.dto.LoginRequestDTO;
import com.example.community.auth.dto.LoginResponseDTO;
import com.example.community.auth.session.RefreshSession;
import com.example.community.auth.session.RefreshSessionStore;
import com.example.community.auth.session.RefreshTokenHasher;
import com.example.community.global.exceptions.*;
import com.example.community.user.dto.*;
import com.example.community.user.entity.User;
import com.example.community.user.entity.UserCredential;
import com.example.community.user.entity.UserRole;
import com.example.community.user.entity.UserStatus;
import com.example.community.user.factory.UserCredentialFactory;
import com.example.community.user.factory.UserFactory;
import com.example.community.user.repository.UserCredentialRepository;
import com.example.community.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    @Mock
    UserRepository userRepository;
    @Mock
    UserCredentialRepository userCredentialRepository;
    @Mock
    JwtTokenProvider jwtTokenProvider;
    @Mock
    RefreshSessionStore refreshSessionStore;
    @Mock
    RefreshTokenHasher refreshTokenHasher;
    @Mock
    AuthValidator authValidator;
    @Mock
    UserFactory userFactory;
    @Mock
    UserCredentialFactory userCredentialFactory;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    User user;
    UserCredential credential;
    JwtToken jwtToken;

    LoginRequestDTO loginRequest;
    SignUpRequestDTO signUpRequest;
    ModifyInfoRequestDTO modifyInfoRequest;
    ModifyPasswordRequestDTO modifyPasswordRequest;

    @BeforeEach
    void setUp() {
        user = new User(1L, "tester", "", UserRole.ROLE_USER, UserStatus.ACTIVE);
        credential = new UserCredential(user, "test@test.com", "encoded-password");

        jwtToken = new JwtToken("Bearer", "access-token", "refresh-token");

        loginRequest = new LoginRequestDTO();
        loginRequest.setEmail("test@test.com");
        loginRequest.setPassword("Test1234!");

        signUpRequest = new SignUpRequestDTO(
                "new@test.com",
                "Test1234!",
                "Test1234!",
                "newbie",
                ""
        );

        modifyInfoRequest = new ModifyInfoRequestDTO();
        modifyInfoRequest.setNickname("newName");
        modifyInfoRequest.setProfileImageUrl("");

        modifyPasswordRequest = new ModifyPasswordRequestDTO();
        modifyPasswordRequest.setPassword("New12345!");
        modifyPasswordRequest.setPasswordConfirm("New12345!");
    }

    @Test
    @DisplayName("로그인 한 유저에게 토큰이 발급 된다.")
    void login_returnsToken(){
        when(userCredentialRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("Test1234!", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createJwtToken(eq(user), anyString())).thenReturn(jwtToken);

        LoginResponseDTO response = userService.login(loginRequest);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getToken()).isEqualTo(jwtToken);
        assertThat(response.getNickname()).isEqualTo("tester");

        ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(jwtTokenProvider).createJwtToken(eq(user), sessionIdCaptor.capture());
        assertThat(sessionIdCaptor.getValue()).isNotBlank();
        verify(passwordEncoder).matches("Test1234!", "encoded-password");
    }

    @Test
    @DisplayName("로그인 성공 시 refresh session을 저장한다.")
    void login_savesRefreshSession(){
        when(userCredentialRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("Test1234!", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createJwtToken(eq(user), anyString())).thenReturn(jwtToken);
        when(jwtTokenProvider.getRemainingValidityMillis("refresh-token")).thenReturn(604800000L);
        when(refreshTokenHasher.hash("refresh-token")).thenReturn("refresh-hash");

        userService.login(loginRequest);

        ArgumentCaptor<RefreshSession> sessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(refreshSessionStore).save(sessionCaptor.capture());

        RefreshSession saved = sessionCaptor.getValue();
        assertThat(saved.userId()).isEqualTo(user.getUserId());
        assertThat(saved.sessionId()).isNotBlank();
        assertThat(saved.refreshTokenHash()).isEqualTo("refresh-hash");
        assertThat(saved.expiresAt()).isAfter(Instant.now());
    }
    @Test
    @DisplayName("이메일이 등록되지 않으면 401")
    void login_emailNotFound_throwsNotRegisteredException() {
        when(userCredentialRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(loginRequest)).isInstanceOf(NotRegisteredException.class);
    }
    @Test
    @DisplayName("비밀번호가 다르면 401")
    void login_passwordInvalid_throwsPasswordInvalidException() {
        loginRequest.setPassword("Wrong1234!");

        when(passwordEncoder.matches("Wrong1234!", "encoded-password")).thenReturn(false);
        when(userCredentialRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> userService.login(loginRequest)).isInstanceOf(PasswordInvalidException.class);
    }

    @Test
    @DisplayName("탈퇴한 유저가 로그인 시도 시 401")
    void login_withDrawnAccount_throwsNotRegisteredException() {
        user.withDraw();
        when(userCredentialRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(credential));
        assertThatThrownBy(() -> userService.login(loginRequest)).isInstanceOf(NotRegisteredException.class);
    }

    @Test
    @DisplayName("로그아웃 성공 시 예외가 발생하지 않는다.")
    void logout_success() {
        assertThatCode(() -> userService.logout(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("회원가입 시 관련 정보가 등록된다.")
    void signUp_success_savesUserAndCredential() {
        User newUser = new User(2L, "newbie", "", UserRole.ROLE_USER, UserStatus.ACTIVE);
        UserCredential newCredential = new UserCredential(newUser, "new@test.com", "encoded-password-2");

        when(userCredentialRepository.existsByEmail(signUpRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByNickname(signUpRequest.getNickname())).thenReturn(false);
        when(userFactory.create(signUpRequest)).thenReturn(newUser);
        when(userCredentialFactory.create(newUser, signUpRequest)).thenReturn(newCredential);

        SignUpResponseDTO response = userService.signUp(signUpRequest);

        assertThat(response.getUserId()).isEqualTo(2L);
        verify(userRepository).save(newUser);
        verify(userCredentialRepository).save(newCredential);
    }
    @Test
    @DisplayName("비밀번호 확인과 비밀번호가 불일치 시 400 에러")
    void signUp_passwordConfirmMismatch_throwsInvalidInputException() {
        signUpRequest.setPasswordConfirm("Wrong1234!");

        assertThatThrownBy(() -> userService.signUp(signUpRequest)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("이미 이메일이 등록되어 있으면 409 에러")
    void signUp_emailAlreadyExists_throwsAlreadyExistsException() {
        when(userCredentialRepository.existsByEmail(signUpRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.signUp(signUpRequest)).isInstanceOf(AlreadyExistsException.class);
    }

    @Test
    @DisplayName("닉네임이 이미 등록되어 있으면 409 에러")
    void signUp_nicknameAlreadyExists_throwsAlreadyExistsException() {
        when(userCredentialRepository.existsByEmail(signUpRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByNickname(signUpRequest.getNickname())).thenReturn(true);

        assertThatThrownBy(() -> userService.signUp(signUpRequest)).isInstanceOf(AlreadyExistsException.class);
    }
    @Test
    @DisplayName("회원 정보 수정 성공 시 정상 처리")
    void modifyInfo_success() {
        when(userRepository.existsByNicknameAndUserIdNot("newName", 1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ModifyInfoResponseDTO response = userService.modifyInfo(1L, 1L, modifyInfoRequest);

        assertThat(response.getNickname()).isEqualTo("newName");
        assertThat(user.getNickname()).isEqualTo("newName");

        verify(authValidator).validateOwner(1L, 1L);
    }
    @Test
    @DisplayName("다른 사람의 정보는 수정 하면 403")
    void modifyInfo_notOwner_throwsForbiddenException() {
        doThrow(new ForbiddenException()).when(authValidator).validateOwner(2L, 1L);

        assertThatThrownBy(() -> userService.modifyInfo(2L, 1L, modifyInfoRequest)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("변경할 닉네임이 이미 존재하면 409")
    void modifyInfo_nicknameAlreadyExists_throwsAlreadyExistsException() {
        when(userRepository.existsByNicknameAndUserIdNot("newName", 1L)).thenReturn(true);

        assertThatThrownBy(() -> userService.modifyInfo(1L, 1L, modifyInfoRequest)).isInstanceOf(AlreadyExistsException.class);
    }
    @Test
    @DisplayName("비밀번호 변경 성공 시 정상 처리")
    void modifyPassword_success() {
        when(userCredentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("New12345!", "encoded-password")).thenReturn(false);
        when(passwordEncoder.encode("New12345!")).thenReturn("new-encoded-password");

        userService.modifyPassword(1L, 1L, modifyPasswordRequest);

        assertThat(credential.getPassword()).isEqualTo("new-encoded-password");
        verify(authValidator).validateOwner(1L, 1L);
        verify(passwordEncoder).matches("New12345!", "encoded-password");
        verify(passwordEncoder).encode("New12345!");
    }
    @Test
    @DisplayName("기존 비밀번호와 같으면 400 에러")
    void modifyPassword_samePassword_throwsInvalidInputException() {
        modifyPasswordRequest.setPassword("Test1234!");
        modifyPasswordRequest.setPasswordConfirm("Test1234!");

        when(userCredentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("Test1234!", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> userService.modifyPassword(1L, 1L, modifyPasswordRequest)).isInstanceOf(InvalidInputException.class);
    }
    @Test
    @DisplayName("다른 사람의 비밀번호는 수정 하면 403")
    void modifyPassword_notOwner_throwsForbiddenException() {
        doThrow(new ForbiddenException()).when(authValidator).validateOwner(2L, 1L);

        assertThatThrownBy(() -> userService.modifyPassword(2L, 1L, modifyPasswordRequest)).isInstanceOf(ForbiddenException.class);
    }
    @Test
    @DisplayName("회원 탈퇴 처리 성공")
    void withdraw_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        WithdrawResponseDTO response = userService.withdraw(1L, 1L);

        assertThat(response.getWithDrawnAt()).isNotNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);

        verify(authValidator).validateOwner(1L, 1L);
    }
    @Test
    @DisplayName("다른 사람의 id로 탈퇴를 시도 하면 403")
    void withdraw_notOwner_throwsForbiddenException() {
        doThrow(new ForbiddenException()).when(authValidator).validateOwner(2L, 1L);

        assertThatThrownBy(() -> userService.withdraw(2L, 1L)).isInstanceOf(ForbiddenException.class);
    }
}
