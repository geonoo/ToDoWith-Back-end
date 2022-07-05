package com.example.backend.user.controller;

import com.example.backend.msg.MsgEnum;
import com.example.backend.user.dto.LoginRequestDto;
import com.example.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class LoginController {
    final private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        log.info("/login");

        Map<String, String> token = userService.login(loginRequestDto);

        return ResponseEntity
                .ok()
                .header(MsgEnum.JWT_HEADER_NAME.getMsg(), token.get(MsgEnum.JWT_HEADER_NAME.getMsg()))
                .header(MsgEnum.REFRESH_HEADER_NAME.getMsg(), token.get(MsgEnum.REFRESH_HEADER_NAME.getMsg()))
                .contentType(new MediaType("applicaton", "text", StandardCharsets.UTF_8))
                .body(MsgEnum.LOGIN_SUCCESS.getMsg());
    }

    @GetMapping("/refresh")
    public ResponseEntity<String> refreshToken (HttpServletRequest request) {
        log.info("/refresh");
        Map<String, String> token = userService.refresh(request);

        return ResponseEntity
                .ok()
                .header(MsgEnum.JWT_HEADER_NAME.getMsg(), token.get(MsgEnum.JWT_HEADER_NAME.getMsg()))
                .header(MsgEnum.REFRESH_HEADER_NAME.getMsg(), token.get(MsgEnum.REFRESH_HEADER_NAME.getMsg()))
                .contentType(new MediaType("applicaton", "text", StandardCharsets.UTF_8))
                .body(MsgEnum.REISSUE_COMPLETED_TOKEN.getMsg());
    }

}
