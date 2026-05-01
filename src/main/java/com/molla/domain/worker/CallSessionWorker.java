package com.molla.domain.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.molla.domain.callsession.CallSession;
import com.molla.domain.callsession.CallSessionRepository;
import com.molla.domain.callsession.SessionEndedEvent;
import com.molla.domain.conversationturn.ConversationTurn;
import com.molla.domain.conversationturn.ConversationTurnRepository;
import com.molla.domain.feedbackreport.FeedbackReportRepository;
import com.molla.domain.feedbackreport.FeedbackReport;
import com.molla.domain.subscription.SubscriptionRepository;
import com.molla.domain.usermemory.UserMemory;
import com.molla.domain.usermemory.UserMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallSessionWorker {

    private final CallSessionRepository callSessionRepository;
    private final ConversationTurnRepository conversationTurnRepository;
    private final FeedbackReportRepository feedbackReportRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserMemoryService userMemoryService;
    private final OpenAiClient openAiClient;
    private final QdrantClient qdrantClient;
    private final ObjectMapper objectMapper;

    /**
     * SessionEndedEvent 수신 후 비동기로 전체 파이프라인 실행.
     * 각 단계가 실패해도 로그만 남기고 다음 단계 계속 진행.
     */
    @Async("workerExecutor")
    @EventListener
    public void processAfterCall(SessionEndedEvent event) {
        String sessionId = event.getSessionId();
        String userId = event.getUserId();
        boolean isLevelTest = event.isLevelTest();

        log.info("워커 시작 — sessionId: {}, userId: {}, isLevelTest: {}", sessionId, userId, isLevelTest);

        // practice 타입 — 구독 없으면 워커 중단
        if (!isLevelTest && !subscriptionRepository.existsActiveByUserId(userId)) {
            log.warn("구독 없음 — 리포트 생성 스킵, sessionId: {}", sessionId);
            return;
        }

        // 발화 목록 조회
        List<ConversationTurn> turns = conversationTurnRepository
                .findBySessionIdOrderBySequenceOrderAsc(sessionId);

        if (turns.isEmpty()) {
            log.warn("발화 기록 없음 — 워커 종료, sessionId: {}", sessionId);
            return;
        }

        CallSession session = callSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            log.error("세션 없음 — 워커 종료, sessionId: {}", sessionId);
            return;
        }

        // ── Step 1: 리포트 생성 ──────────────────────
        String reportJson = null;
        FeedbackReport savedReport = null;

        try {
            reportJson = openAiClient.generateReport(turns, session.getSessionType());
            savedReport = saveReport(sessionId, session.getSessionType(), reportJson);
            log.info("Step 1 완료 — 리포트 생성, sessionId: {}", sessionId);
        } catch (Exception e) {
            log.error("Step 1 실패 — 리포트 생성 오류, sessionId: {}, error: {}", sessionId, e.getMessage(), e);
        }

        // ── Step 2: user_memories 갱신 ──────────────
        try {
            if (reportJson != null) {
                UserMemory existingMemory = userMemoryService.getMemory(userId);
                String memorySummaryJson = openAiClient.generateMemorySummary(existingMemory, reportJson);
                upsertUserMemory(userId, memorySummaryJson, session.getDurationSeconds());
                log.info("Step 2 완료 — user_memories 갱신, userId: {}", userId);
            }
        } catch (Exception e) {
            log.error("Step 2 실패 — user_memories 갱신 오류, userId: {}, error: {}", userId, e.getMessage(), e);
        }

        // ── Step 3: Qdrant upsert ────────────────────
        try {
            qdrantClient.upsertTurns(sessionId, userId, turns);
            log.info("Step 3 완료 — Qdrant upsert, sessionId: {}", sessionId);
        } catch (Exception e) {
            log.error("Step 3 실패 — Qdrant upsert 오류, sessionId: {}, error: {}", sessionId, e.getMessage(), e);
        }

        log.info("워커 완료 — sessionId: {}", sessionId);
    }

    // ──────────────────────────────────────────────
    // 내부 유틸
    // ──────────────────────────────────────────────

    private FeedbackReport saveReport(String sessionId, String sessionType, String reportJson) throws Exception {
        JsonNode node = objectMapper.readTree(reportJson);

        FeedbackReport report = FeedbackReport.create(
                sessionId,
                sessionType,
                getTextOrNull(node, "oneLineSummary"),
                toJsonString(node, "grammarCorrections"),
                toJsonString(node, "vocabularySuggestions"),
                toJsonString(node, "habitAnalysis"),
                getTextOrNull(node, "pronunciationNotes"),
                node.path("overallScore").isNull() ? null : (float) node.path("overallScore").asDouble(),
                getTextOrNull(node, "levelResult")
        );

        return feedbackReportRepository.save(report);
    }

    private void upsertUserMemory(String userId, String memorySummaryJson, Integer durationSeconds) throws Exception {
        JsonNode node = objectMapper.readTree(memorySummaryJson);

        int addedMinutes = durationSeconds != null ? (int) Math.ceil(durationSeconds / 60.0) : 0;

        userMemoryService.upsertMemory(
                userId,
                getTextOrNull(node, "summary"),
                toJsonString(node, "weakPoints"),
                toJsonString(node, "habitPatterns"),
                toJsonString(node, "interests"),
                getTextOrNull(node, "goals"),
                addedMinutes
        );
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private String toJsonString(JsonNode node, String field) throws Exception {
        JsonNode value = node.path(field);
        if (value.isNull() || value.isMissingNode()) return null;
        return objectMapper.writeValueAsString(value);
    }
}
