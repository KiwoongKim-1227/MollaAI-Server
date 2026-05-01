package com.molla.domain.chatmessage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    /**
     * 특정 세션의 채팅 메시지 목록 — 시간순 오름차순
     * userId 조건을 함께 걸어 본인 세션만 조회
     */
    List<ChatMessage> findBySessionIdAndUserIdOrderByCreatedAtAsc(String sessionId, String userId);
}
