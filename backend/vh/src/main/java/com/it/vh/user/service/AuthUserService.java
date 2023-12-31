package com.it.vh.user.service;

import com.it.vh.common.util.jwt.JwtTokenProvider;
import com.it.vh.common.util.jwt.dto.TokenInfo;
import com.it.vh.user.api.dto.auth.*;
import com.it.vh.user.api.dto.auth.LoginResDto.UserProfile;
import com.it.vh.user.domain.entity.User;
import com.it.vh.user.domain.repository.UserRespository;
import com.it.vh.user.exception.NonExistUserIdException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class AuthUserService {

    private final InMemoryClientRegistrationRepository inMemoryRepository;
    private final UserRespository userRespository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRedisService userRedisService;

    @Transactional
    public LoginResDto login(String code, String provider) {
        ClientRegistration providerInfo = inMemoryRepository.findByRegistrationId(provider);

        //토큰 받기
        AuthTokenInfo token = getToken(code, providerInfo);
        String unlinkToken = token.getAccess_token();
        log.info("kakaoToken: {}", unlinkToken);

        //가입 또는 로그인 처리
        User user = getUser(token, providerInfo, provider);
        log.info("[사용자] user: {}", user);

        UserProfile userProfile = null;
        if (user.getNickname() == "") {
            userProfile = UserProfile.builder()
                    .userId(user.getUserId())
                    .nickname(user.getNickname())
                    .statusMsg(user.getStatusMsg())
                    .profileImg(user.getProfileImg())
                    .snsEmail(user.getSnsEmail())
                    .provider(user.getProvider())
                    .isAlready(0)
                    .build();
        } else {
            userProfile = UserProfile.builder()
                    .userId(user.getUserId())
                    .nickname(user.getNickname())
                    .statusMsg(user.getStatusMsg())
                    .profileImg(user.getProfileImg())
                    .snsEmail(user.getSnsEmail())
                    .provider(user.getProvider())
                    .isAlready(1)
                    .build();
        }

        log.info("userProfile: {}", userProfile);

        //권한 설정
        Authentication authentication = jwtTokenProvider.setAuthentication(user);
        TokenInfo tokenInfo = jwtTokenProvider.generateToken(authentication);

        //권한 확인
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//        Long userId = Long.parseLong(auth.getName());
//        log.info("userId: {}", userId);

        //레디스에 리프레시 토큰 저장
        userRedisService.saveRefreshToken(String.valueOf(user.getUserId()),
                tokenInfo.getRefreshToken());

        return LoginResDto.builder()
                .user(userProfile)
                .token(tokenInfo)
                .unlinkToken(unlinkToken)
                .build();
    }

    private AuthTokenInfo getToken(String code, ClientRegistration provider) {
        return WebClient.create()
                .post()
                .uri(provider.getProviderDetails().getTokenUri())
                .headers(header -> {
                    header.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                    header.setAcceptCharset(Collections.singletonList(StandardCharsets.UTF_8));
                })
                .bodyValue(tokenRequest(code, provider))
                .retrieve()
                .bodyToMono(AuthTokenInfo.class)
                .block();
    }

    private MultiValueMap<String, String> tokenRequest(String code, ClientRegistration provider) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", provider.getClientId());
        formData.add("redirect_uri", provider.getRedirectUri());
        formData.add("code", code);
        formData.add("client_secret", provider.getClientSecret());
        return formData;
    }

    @Transactional
    public User getUser(AuthTokenInfo token, ClientRegistration providerInfo, String provider) {
        Map<String, Object> userAttributes = getUserAttributes(token, providerInfo);

        AuthUserInfo oAuth2UserInfo = null;
        if (provider.equals("kakao")) {
            oAuth2UserInfo = new KakaoUserInfo(userAttributes);
        } else if (provider.equals("google")) {
            oAuth2UserInfo = new GoogleUserInfo(userAttributes);
        }
        String snsEmail = oAuth2UserInfo.getEmail();

        Optional<User> findUser = userRespository.findBySnsEmailAndProvider(snsEmail, provider);
        if (findUser.isPresent()) {
            log.info("[이미 등록된 사용자] userId: {}", findUser.get().getUserId());
            return findUser.get();
        }

        User saveUser = User.builder()
                .nickname("")
                .snsEmail(snsEmail)
                .provider(provider)
                .build();

        userRespository.save(saveUser);
        log.info("[가입완료] userId: {}", saveUser.getUserId());
        return saveUser;
    }

    private Map<String, Object> getUserAttributes(AuthTokenInfo token,
                                                  ClientRegistration provider) {
        return WebClient.create()
                .get()
                .uri(provider.getProviderDetails().getUserInfoEndpoint().getUri())
                .headers(header -> {
                    header.setBearerAuth(token.getAccess_token());
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .block();
    }

    public TokenInfo setToken(Long userId) {
        Optional<User> findUser = userRespository.findUserByUserId(userId);
        if (!findUser.isPresent())
            throw new NonExistUserIdException();

        //권한 설정
        Authentication authentication = jwtTokenProvider.setAuthentication(findUser.get());
        TokenInfo tokenInfo = jwtTokenProvider.generateToken(authentication);

        //권한 확인
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//        Long userId = Long.parseLong(auth.getName());
//        log.info("userId: {}", userId);

        //레디스에 리프레시 토큰 저장
        userRedisService.saveRefreshToken(String.valueOf(userId),
                tokenInfo.getRefreshToken());

        return tokenInfo;
    }
}
