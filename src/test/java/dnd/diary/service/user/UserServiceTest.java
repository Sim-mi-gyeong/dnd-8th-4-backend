package dnd.diary.service.user;

import dnd.diary.config.Jwt.TokenProvider;
import dnd.diary.config.redis.RedisDao;
import dnd.diary.domain.user.Authority;
import dnd.diary.domain.user.User;
import dnd.diary.repository.user.UserRepository;
import dnd.diary.request.controller.user.UserRequest;
import dnd.diary.response.user.UserResponse;
import dnd.diary.response.user.UserSearchResponse;
import dnd.diary.service.redis.RedisService;
import dnd.diary.service.s3.S3Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @MockBean
    private RedisService redisService;

    @MockBean
    private S3Service s3Service;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private UserService userService;


    @Autowired
    private AuthenticationManagerBuilder authenticationManagerBuilder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @DisplayName("유저가 회원가입을 하고 토큰을 발급 받는다.")
    @Test
    void createUserAccount() {
        // given
        UserRequest.CreateUser request = new UserRequest.CreateUser(
                "test@test.com", "abc123!", "테스트 계정",
                "테스트 닉네임", "010-1234-5678", ""
        );


        // when
        UserResponse.Login response = userService.createUserAccount(request.toServiceRequest());

        // then
        assertThat(response)
                .extracting("email", "name", "nickName", "phoneNumber")
                .contains("test@test.com", "테스트 계정", "테스트 닉네임", "010-1234-5678");

        assertThat(response.getProfileImageUrl()).isNotBlank();
        assertThat(response.getAtk()).isNotBlank();
        assertThat(response.getRtk()).isNotBlank();
    }

    @DisplayName("유저가 로그인을 하고 토큰을 발급 받는다.")
    @Test
    void login() {
        getUserAndSave();

        // given
        UserRequest.Login request = new UserRequest.Login("test@test.com", "abc123!");

        // when
        UserResponse.Login response = userService.login(request.toServiceRequest());

        // then
        assertThat(response)
                .extracting("email", "name", "nickName", "phoneNumber")
                .contains("test@test.com", "테스트 계정", "테스트 닉네임", "010-1234-5678");

        assertThat(response.getProfileImageUrl()).isNotBlank();
        assertThat(response.getAtk()).isNotBlank();
        assertThat(response.getRtk()).isNotBlank();
    }

    @DisplayName("유저가 자신의 정보를 조회한다.")
    @Test
    void findMyListUser() {
        // given
        User user = getUserAndSave();

        // when
        UserResponse.Detail response = userService.findMyListUser(user.getId());

        // then
        assertThat(response)
                .extracting("email", "name", "nickName", "phoneNumber")
                .contains("test@test.com", "테스트 계정", "테스트 닉네임", "010-1234-5678");
    }

    @DisplayName("유저가 자신의 정보를 수정한다.")
    @Test
    void userUpdateProfile() {
        // given
        User user = getUserAndSave();
        MultipartFile file = new MockMultipartFile("file", "text", "text/plain", "Spring Framework".getBytes());

        given(s3Service.saveProfileImage(any(MultipartFile.class)))
                .willReturn("updateImage");
        // when
        UserResponse.Update response = userService.userUpdateProfile(user.getId(), "업데이트 계정", file);

        // then
        assertThat(response)
                .extracting("nickName", "profileImageUrl")
                .contains("업데이트 계정", "updateImage");
    }

    @DisplayName("유저가 로그아웃을 하면 액세스 토큰이 블랙리스트로 redis 에 저장된다.")
    @Test
    void logoutUser() {
        // given
        User user = getUserAndSave();
        Authentication authentication = saveSecurityContextHolderAndGetAuthentication();
        String testAccessToken = tokenProvider.createToken(user.getId(), authentication);

        given(redisService.logoutFromRedis(anyString(), anyString(), anyLong()))
                .willReturn(true);

        // when
        Boolean response = userService.logout(user.getId(), testAccessToken);

        // then
        assertThat(response).isTrue();
    }

    /*  Redis Service 테스트 (분리 예정)

        @DisplayName("유저가 로그아웃을 하면 액세스 토큰이 블랙리스트로 redis 에 저장된다.")
    @Test
    void logoutUser() {
        // given
        User user = getUserAndSave();
        Authentication authentication = saveSecurityContextHolderAndGetAuthentication();

        String testAccessToken = tokenProvider.createToken(user.getId(), authentication);
        redisDao.setValues(user.getEmail(), "testRefreshToken");

        // when
        userService.logout(user.getId(), testAccessToken);

        // then
        assertThat(redisDao.getValues(user.getEmail())).isNull();
        assertThat(redisDao.getValues(testAccessToken)).isEqualTo("logout");
    }
     */

    @DisplayName("유저가 서비스 회원탈퇴를 진행한다.")
    @Test
    void userDelete() {
        // given
        User user = getUserAndSave();
        Authentication authentication = saveSecurityContextHolderAndGetAuthentication();
        String testAccessToken = tokenProvider.createToken(user.getId(), authentication);

        given(redisService.logoutFromRedis(anyString(), anyString(), anyLong()))
                .willReturn(true);
        // when
        Boolean response = userService.deleteUser(user.getId(), testAccessToken);

        // then
        assertThat(response).isTrue();

        Optional<User> userOptional = userRepository.findById(user.getId());
        assertThat(userOptional.isEmpty()).isTrue();
    }

    @DisplayName("키워드로 해당 서비스에 가입되어있는 유저를 검색한다.")
    @Test
    void searchUserList() {
        // given
        User user1 = getUserAndSave("test1@test.com", "테스트 계정1");
        User user2 = getUserAndSave("test2@test.com", "테스트 계정2");

        // when
        UserSearchResponse response = userService.searchUserList("테스트");

        // then
        assertThat(response.getUserSearchInfoList())
                .hasSize(2)
                .extracting(UserSearchResponse.UserSearchInfo::getUserEmail, UserSearchResponse.UserSearchInfo::getUserNickName)
                .contains(
                        tuple("test1@test.com", "테스트 계정1"),
                        tuple("test2@test.com", "테스트 계정2")
                );
    }

    // method
    private User getUserAndSave() {
        User user = User.builder()
                .authorities(getAuthorities())
                .email("test@test.com")
                .password(passwordEncoder.encode("abc123!"))
                .name("테스트 계정")
                .nickName("테스트 닉네임")
                .phoneNumber("010-1234-5678")
                .profileImageUrl("test.png")
                .mainLevel(0L)
                .subLevel(0.0)
                .isNewNotification(Boolean.FALSE)
                .build();

        return userRepository.save(user);
    }

    private User getUserAndSave(String email, String nickName) {
        User user = User.builder()
                .authorities(getAuthorities())
                .email(email)
                .password(passwordEncoder.encode("abc123!"))
                .name("테스트 계정")
                .nickName(nickName)
                .phoneNumber("010-1234-5678")
                .profileImageUrl("test.png")
                .mainLevel(0L)
                .subLevel(0.0)
                .isNewNotification(Boolean.FALSE)
                .build();

        return userRepository.save(user);
    }

    private static Set<Authority> getAuthorities() {
        return Collections.singleton(Authority.builder()
                .authorityName("ROLE_USER")
                .build());
    }

    private Authentication saveSecurityContextHolderAndGetAuthentication() {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken("test@test.com", "abc123!");
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return authentication;
    }
}