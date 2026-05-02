package com.molla.domain.chatmessage;

import com.molla.common.exception.GlobalException;
import com.molla.common.response.ErrorCode;
import com.molla.controller.dto.chatmessage.ChatExchangeResponse;
import com.molla.controller.dto.chatmessage.ChatMessageResponse;
import com.molla.controller.dto.chatmessage.SendMessageRequest;
import com.molla.domain.callsession.CallSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final CallSessionRepository callSessionRepository;
    private final ChatAiClient chatAiClient;

    public List<ChatMessageResponse> getMessages(String sessionId, String userId) {
        callSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.SESSION_NOT_FOUND));

        return chatMessageRepository
                .findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    public ChatExchangeResponse sendMessage(String sessionId, String userId, SendMessageRequest request) {
        callSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.SESSION_NOT_FOUND));

        // 1. 유저 메시지 먼저 저장 (별도 트랜잭션 — AI 실패해도 유저 메시지 보존)
        ChatMessageResponse userMsg = saveUserMessage(sessionId, userId, request.content());

        // 2. 히스토리 조회 — 방금 저장한 유저 메시지 포함된 상태
        //    buildMessages()에서 userMessage를 다시 append하지 않으므로 중복 없음
        List<ChatMessage> history = chatMessageRepository
                .findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId);

        // 3. AI 응답 생성
        String aiReply = chatAiClient.generateReply(sessionId, history);

        // 4. AI 응답 저장
        ChatMessage aiMessage = ChatMessage.create(userId, sessionId, "ai", aiReply);
        chatMessageRepository.save(aiMessage);

        log.info("채팅 메시지 처리 완료 — sessionId: {}, userId: {}", sessionId, userId);

        return ChatExchangeResponse.of(userMsg, ChatMessageResponse.from(aiMessage));
    }

    @Transactional
    public ChatMessageResponse saveUserMessage(String sessionId, String userId, String content) {
        ChatMessage userMessage = ChatMessage.create(userId, sessionId, "user", content);
        chatMessageRepository.save(userMessage);
        return ChatMessageResponse.from(userMessage);
    }
}
