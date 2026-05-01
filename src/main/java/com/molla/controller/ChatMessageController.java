package com.molla.controller;

import com.molla.common.response.ApiResponse;
import com.molla.controller.dto.chatmessage.ChatExchangeResponse;
import com.molla.controller.dto.chatmessage.ChatMessageResponse;
import com.molla.controller.dto.chatmessage.SendMessageRequest;
import com.molla.domain.chatmessage.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ChatMessage", description = "채팅 API — 통화 후 AI와 텍스트 채팅")
@RestController
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @Operation(
            summary = "채팅 메시지 목록 조회",
            description = """
                    특정 세션에 연결된 채팅 메시지 전체를 시간순으로 반환합니다.
                    - 통화 후 이어지는 AI 코칭 채팅 내역을 표시할 때 사용합니다.
                    - 본인 세션만 조회 가능합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "세션 없음 또는 권한 없음",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/api/v1/chat/{sessionId}")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(
            @PathVariable String sessionId
    ) {
        String userId = getCurrentUserId();
        List<ChatMessageResponse> response = chatMessageService.getMessages(sessionId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "채팅 메시지 전송",
            description = """
                    유저 메시지를 저장하고 AI 응답을 생성해서 함께 반환합니다.
                    - 유저 메시지와 AI 응답이 한 쌍으로 반환됩니다.
                    - 세션 리포트가 있으면 AI가 통화 내용을 참고해서 답변합니다.
                    - AI 응답은 한국어로 생성되며, 영어 예시는 영어로 포함됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "전송 및 AI 응답 생성 성공",
                    content = @Content(schema = @Schema(implementation = ChatExchangeResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "세션 없음 또는 권한 없음",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/api/v1/chat/{sessionId}")
    public ResponseEntity<ApiResponse<ChatExchangeResponse>> sendMessage(
            @PathVariable String sessionId,
            @RequestBody @Valid SendMessageRequest request
    ) {
        String userId = getCurrentUserId();
        ChatExchangeResponse response = chatMessageService.sendMessage(sessionId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String getCurrentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
