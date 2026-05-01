package com.molla.controller.dto.chatmessage;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "채팅 메시지 전송 요청")
public record SendMessageRequest(

        @Schema(description = "유저가 보낸 메시지 내용", example = "오늘 통화에서 제가 자주 틀린 문법이 뭔가요?")
        @NotBlank(message = "메시지 내용을 입력해주세요.")
        @Size(max = 2000, message = "메시지는 2000자 이하여야 합니다.")
        String content
) {}
