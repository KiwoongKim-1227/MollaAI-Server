package com.molla.controller.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "앱 회원가입 요청")
public record RegisterRequest(

        @Schema(description = "닉네임", example = "홍길동")
        @NotBlank(message = "닉네임을 입력해주세요.")
        @Size(min = 2, max = 50, message = "닉네임은 2~50자입니다.")
        String username,

        @Schema(description = "비밀번호 (8자 이상)", example = "password123!")
        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password
) {}
