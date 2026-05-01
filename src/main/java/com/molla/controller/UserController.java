package com.molla.controller;

import com.molla.common.response.ApiResponse;
import com.molla.controller.dto.user.RegisterRequest;
import com.molla.controller.dto.user.UpdateUserRequest;
import com.molla.controller.dto.user.UserResponse;
import com.molla.domain.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "유저 API — 회원가입, 내 정보 조회/수정/탈퇴")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "앱 회원가입",
            description = """
                    SMS 인증 후 발급받은 JWT로 앱 가입을 완료합니다.
                    - username, password 등록
                    - is_registered = true 로 업데이트
                    - 이미 가입된 유저가 재요청하면 409 반환
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "가입 성공",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이미 가입된 유저",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @RequestBody @Valid RegisterRequest request
    ) {
        String userId = getCurrentUserId();
        UserResponse response = userService.register(userId, request);
        return ResponseEntity.ok(ApiResponse.success("회원가입이 완료되었습니다.", response));
    }

    @Operation(
            summary = "내 정보 조회",
            description = "JWT로 인증된 유저의 정보를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "유저 없음",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/api/v1/users/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe() {
        String userId = getCurrentUserId();
        UserResponse response = userService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "내 정보 수정",
            description = "닉네임, 영어 레벨을 수정합니다. null로 보낸 필드는 수정하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/api/v1/users/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @RequestBody @Valid UpdateUserRequest request
    ) {
        String userId = getCurrentUserId();
        UserResponse response = userService.updateMe(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "유저 status를 withdrawn으로 변경하고 Refresh Token을 삭제합니다. 탈퇴 후 동일 전화번호로 재가입은 가능합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "탈퇴 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @DeleteMapping("/api/v1/users/me")
    public ResponseEntity<ApiResponse<Void>> withdraw() {
        String userId = getCurrentUserId();
        userService.withdraw(userId);
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴가 완료되었습니다.", null));
    }

    // ──────────────────────────────────────────────
    // SecurityContextHolder에서 userId 추출
    // JwtAuthenticationFilter에서 principal = userId로 세팅했으므로 바로 꺼냄
    // ──────────────────────────────────────────────

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (String) authentication.getPrincipal();
    }
}
