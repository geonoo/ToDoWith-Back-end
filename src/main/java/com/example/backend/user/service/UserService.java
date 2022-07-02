package com.example.backend.user.service;

import com.example.backend.exception.CustomException;
import com.example.backend.exception.ErrorCode;
import com.example.backend.msg.MsgEnum;
import com.example.backend.user.config.AppProperties;
import com.example.backend.user.domain.*;
import com.example.backend.user.dto.EmailCheckRequestDto;
import com.example.backend.user.dto.LoginRequestDto;
import com.example.backend.user.dto.RegisterRequestDto;
import com.example.backend.user.dto.UserResponseDto;
import com.example.backend.user.repository.EmailCheckRepository;
import com.example.backend.user.repository.UserRefreshTokenRepository;
import com.example.backend.user.repository.UserRepository;
import com.example.backend.user.token.AuthToken;
import com.example.backend.user.token.AuthTokenProvider;
import com.example.backend.user.utils.HeaderUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final JavaMailSender javaMailSender;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final EmailCheckRepository emailCheckRepository;
    private final AppProperties appProperties;
    private final AuthTokenProvider tokenProvider;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final static long THREE_DAYS_MSEC = 259200000;

    @Value("${spring.mail.username}")
    private String adminMail;

    public String emailCertification(String email) {

        //중복이메일 체크
        dupleEmailCheck(email);

        //SMTP 이메일 전송 셋팅 / 인증번호 생성
        SimpleMailMessage simpleMessage = new SimpleMailMessage();
        simpleMessage.setFrom(adminMail);
        simpleMessage.setTo(email);
        simpleMessage.setSubject(MsgEnum.EMAIL_TITLE.getMsg());

        String code = ThreadLocalRandom.current().nextInt(100000, 1000000)+"";

        EmailCheck emailCheck = EmailCheck.builder()
                .email(email)
                .code(code)
                .build();

        emailCheckRepository.save(emailCheck);

        simpleMessage.setText(MsgEnum.EMAIL_CONTENT_FRONT.getMsg()+ code);

        javaMailSender.send(simpleMessage);

        return MsgEnum.EMAIL_SEND.getMsg();
    }

    @Transactional
    public String emailCertificationCheck(EmailCheckRequestDto emailCheckDto) {
        List<EmailCheck> emailCheck = getEmailCheckList(emailCheckDto.getEmail());
        EmailCheck firstValue = emailCheck.get(0);
        if (firstValue.getCode().equals(emailCheckDto.getCode())){
            //인증 완료시 Y로 바꾸기
            firstValue.verificationCompleted("Y");
            return MsgEnum.CORRECT_EMAIL_CODE.getMsg();
        }

        throw new IllegalArgumentException(ErrorCode.INCORRECT_EMAIL_CODE.getMsg());

    }

    public String nickCheck(RegisterRequestDto registerDto) {
        //중복 닉네임 체크
        dupleNickCheck(registerDto.getNick());

        return MsgEnum.AVAILABLE_NICK.getMsg();
    }

    @Transactional
    public String register(RegisterRequestDto registerDto) {
        //이메일 중복
        dupleEmailCheck(registerDto.getEmail());
        //닉네임 중복
        dupleNickCheck(registerDto.getNick());
        //인증 메일 보냈나 확인
        List<EmailCheck> emailChecks = getEmailCheckList(registerDto.getEmail());
        if (emailChecks.get(0).getConfirmYn().equals("N")){
            throw new IllegalArgumentException(ErrorCode.INCORRECT_EMAIL_CODE.getMsg());
        }

        User user = User.builder()
                .email(registerDto.getEmail())
                .username(registerDto.getNick())
                .password(passwordEncoder.encode(registerDto.getPassword()))
                .roleType(RoleType.USER)
                .providerType(ProviderType.LOCAL)
                .build();

        userRepository.save(user);

        //인증한 이메일 삭제
        emailCheckRepository.deleteByEmail(user.getEmail());

        return MsgEnum.REGISTER_SUCCESS.getMsg();
    }

    private void dupleEmailCheck(String email) {
        if(userRepository.findByEmail(email).isPresent()){
            throw new IllegalArgumentException(ErrorCode.DUPLE_EMAIL.getMsg());
        }
    }

    private void dupleNickCheck(String nick) {
        if(userRepository.findByUsername(nick).isPresent()){
            throw new IllegalArgumentException(ErrorCode.DUPLE_NICK.getMsg());
        }
    }

    private List<EmailCheck> getEmailCheckList(String email) {
        List<EmailCheck> emailCheck = emailCheckRepository.findByEmailOrderByCreatedDateDesc(email);
        if (emailCheck.size() == 0){
            throw new IllegalArgumentException(ErrorCode.INCORRECT_EMAIL_CODE.getMsg());
        }
        return emailCheck;
    }


    @Transactional
    public Map<String, String> login(LoginRequestDto loginRequestDto) {
        //회원 있는지 없는지 체크
        User user = userRepository.findByEmail(loginRequestDto.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.CONFIRM_EMAIL_PWD));

        //비밀번호 체크
        if (!passwordEncoder.matches(loginRequestDto.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.CONFIRM_EMAIL_PWD);
        }

        //create accessToken
        Date now = new Date();
        AuthToken accessToken = tokenProvider.createAuthToken(
                user.getEmail(),
                RoleType.USER.getCode(),
                user.getUsername(),
                new Date(now.getTime() + appProperties.getAuth().getTokenExpiry())
        );

        //create refreshToken
        long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();
        AuthToken refreshToken = tokenProvider.createAuthToken(
                appProperties.getAuth().getTokenSecret(),
                new Date(now.getTime() + refreshTokenExpiry)
        );

        // userId refresh token 으로 DB 확인
        UserRefreshToken userRefreshToken = userRefreshTokenRepository.findByEmail(user.getEmail());
        if (userRefreshToken == null) {
            // 없는 경우 새로 등록
            userRefreshToken = new UserRefreshToken(user.getEmail(), refreshToken.getToken());
            userRefreshTokenRepository.saveAndFlush(userRefreshToken);
        } else {
            // DB에 refresh 토큰 업데이트
            userRefreshToken.setRefreshToken(refreshToken.getToken());
        }

        //토큰 Map에 넣고 리턴
        Map<String, String> token = new HashMap<>();
        token.put(MsgEnum.JWT_HEADER_NAME.getMsg(), accessToken.getToken());
        token.put(MsgEnum.REFRESH_HEADER_NAME.getMsg(), userRefreshToken.getRefreshToken());
        return token;
    }



    @Transactional
    public Map<String, String> refresh(HttpServletRequest request){
        String accessToken = HeaderUtil.getAccessToken(request);
        AuthToken authToken = tokenProvider.convertAuthToken(accessToken);

        // 만료된 access token 인지 확인
        Claims claims = authToken.getExpiredTokenClaims();
        if (claims == null) {
            throw new IllegalArgumentException(MsgEnum.NOT_EXPIRED_TOKEN_YET.getMsg());
        }

        String email = claims.getSubject();
        String username = claims.get("nick", String.class);
        RoleType roleType = RoleType.of(claims.get("role", String.class));

        // refresh token
        String refreshToken = HeaderUtil.getRefreshToken(request);
        AuthToken authRefreshToken = tokenProvider.convertAuthToken(refreshToken);
        if (!authRefreshToken.validate()) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // userId refresh token 으로 DB 확인
        UserRefreshToken userRefreshToken = userRefreshTokenRepository.findByEmailAndRefreshToken(email, refreshToken);
        if (userRefreshToken == null) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        //새로운 AccessToken 생성
        Date now = new Date();
        AuthToken newAccessToken = tokenProvider.createAuthToken(
                email,
                roleType.getCode(),
                username,
                new Date(now.getTime() + appProperties.getAuth().getTokenExpiry())
        );

        // refresh 토큰 기간이 3일 이하로 남은 경우, refresh 토큰 갱신
        long validTime = authRefreshToken.getTokenClaims().getExpiration().getTime() - now.getTime();
        if (validTime <= THREE_DAYS_MSEC) {
            // refresh 토큰 설정
            long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();

            authRefreshToken = tokenProvider.createAuthToken(
                    appProperties.getAuth().getTokenSecret(),
                    new Date(now.getTime() + refreshTokenExpiry)
            );
            // DB에 refresh 토큰 업데이트 해주기
            userRefreshToken.setRefreshToken(authRefreshToken.getToken());
        }

        //토큰 Map에 넣고 리턴
        Map<String, String> token = new HashMap<>();
        token.put(MsgEnum.JWT_HEADER_NAME.getMsg(), newAccessToken.getToken());
        token.put(MsgEnum.REFRESH_HEADER_NAME.getMsg(), authRefreshToken.getToken());
        return token;
    }

    @Transactional
    public Map<String, String> addNick(String email, String nick) {

        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new CustomException(ErrorCode.USER_NOT_FOUND)
        );

        dupleNickCheck(nick);
        user.addNick(nick);

        Date now = new Date();
        AuthToken newAccessToken = tokenProvider.createAuthToken(
                email,
                user.getRoleType().getCode(),
                user.getUsername(),
                new Date(now.getTime() + appProperties.getAuth().getTokenExpiry())
        );

        long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();
        AuthToken refreshToken = tokenProvider.createAuthToken(
                appProperties.getAuth().getTokenSecret(),
                new Date(now.getTime() + refreshTokenExpiry)
        );

        UserRefreshToken userRefreshToken = userRefreshTokenRepository.findByEmail(email);
        if (userRefreshToken == null) {
            // 없는 경우 새로 등록
            userRefreshToken = new UserRefreshToken(email, refreshToken.getToken());
            userRefreshTokenRepository.saveAndFlush(userRefreshToken);
        } else {
            // DB에 refresh 토큰 업데이트
            userRefreshToken.setRefreshToken(refreshToken.getToken());
        }

        Map<String, String> token = new HashMap<>();
        token.put(MsgEnum.JWT_HEADER_NAME.getMsg(), newAccessToken.getToken());
        token.put(MsgEnum.REFRESH_HEADER_NAME.getMsg(), userRefreshToken.getRefreshToken());
        return token;
    }

    public UserResponseDto getUser(String email) {
        UserResponseDto userResponseDto = new UserResponseDto(
            userRepository.findByEmail(email).orElseThrow(
                    () -> new CustomException(ErrorCode.USER_NOT_FOUND)
            )
        );
        return userResponseDto;
    }
}