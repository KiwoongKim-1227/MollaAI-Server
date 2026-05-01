package com.molla.controller;

import com.molla.common.response.ApiResponse;
import com.molla.controller.dto.auth.*;
import com.molla.domain.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API — SMS 인증 및 JWT 발급")
@RestController
@RequiredArgsConstructor
@SecurityRequirements // 인증 불필요 — Swagger 전역 Security override
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "인증번호 SMS 발송",
            description = "입력한 전화번호로 6자리 인증번호를 SMS 발송합니다. 유효 시간은 5분이며, 재요청 시 기존 코드는 무효 처리됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발송 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "전화번호 형식 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "SMS 발송 실패",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/api/v1/auth/send-code")
    public ResponseEntity<ApiResponse<Void>> sendCode(
            @RequestBody @Valid SendCodeRequest request
    ) {
        authService.sendVerificationCode(request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.success("인증번호가 발송되었습니다.", null));
    }

    @Operation(
            summary = "인증번호 확인 + JWT 발급",
            description = """
                    인증번호를 확인하고 JWT를 발급합니다.
                    - 인증 성공 시 Access Token(1시간), Refresh Token(30일) 반환
                    - 최초 인증 유저: `isNewUser: true` → 앱 가입 플로우 진행
                    - 기존 유저: `isNewUser: false` → 바로 로그인 처리
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "인증 성공 + JWT 발급",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "인증번호 불일치 또는 만료",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/api/v1/auth/verify-code")
    public ResponseEntity<ApiResponse<TokenResponse>> verifyCode(
            @RequestBody @Valid VerifyCodeRequest request
    ) {
        TokenResponse token = authService.verifyCode(request.phoneNumber(), request.code());
        return ResponseEntity.ok(ApiResponse.success(token));
    }

    @Operation(
            summary = "Access Token 재발급",
            description = "만료된 Access Token을 Refresh Token으로 재발급합니다. Refresh Token은 DB 저장값과 대조하며, 30일 후 만료됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "재발급 성공",
                    content = @Content(schema = @Schema(implementation = AccessTokenResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Refresh Token 무효 또는 만료",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/api/v1/auth/refresh")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> refresh(
            @RequestBody @Valid RefreshTokenRequest request
    ) {
        AccessTokenResponse token = authService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(token));
    }
}
