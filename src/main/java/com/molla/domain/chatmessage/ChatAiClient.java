package com.molla.domain.chatmessage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.molla.common.response.ErrorCode;
import com.molla.domain.feedbackreport.FeedbackReport;
import com.molla.domain.feedbackreport.FeedbackReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

//OpenAiClient를 직접 쓰면 워커 전용 로직과 섞이므로, 채팅 전용 AI 호출 클라이언트를 분리

@Slf4j
@Component
public class ChatAiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final FeedbackReportRepository feedbackReportRepository;
    private final String model;

    public ChatAiClient(
            @Qualifier("openAiWebClient") WebClient webClient,
            ObjectMapper objectMapper,
            FeedbackReportRepository feedbackReportRepository,
            @Value("${openai.model}") String model
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.feedbackReportRepository = feedbackReportRepository;
        this.model = model;
    }

    /**
     * 유저 메시지에 대한 AI 응답 생성.
     * 세션 리포트가 있으면 컨텍스트로 포함해서 더 정확한 피드백 제공.
     *
     * @param sessionId  연결된 세션 ID (nullable)
     * @param userMessage 유저 메시지
     * @param history    이전 대화 히스토리 (최근 10개)
     * @return AI 응답 텍스트
     */
    public String generateReply(String sessionId, String userMessage, List<ChatMessage> history) {
        String reportContext = buildReportContext(sessionId);

        String systemPrompt = """
                당신은 친절한 영어 학습 코치입니다. 학습자의 질문에 한국어로 답변하되,
                영어 예시나 교정은 영어로 작성하세요.
                """ + reportContext;

        List<Map<String, String>> messages = buildMessages(systemPrompt, history, userMessage);

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", 0.7,
                    "max_tokens", 1000
            );

            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            log.error("AI 채팅 응답 생성 실패: {}", e.getMessage(), e);
            throw new ChatMessageException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // ──────────────────────────────────────────────
    // 내부 유틸
    // ──────────────────────────────────────────────

    private String buildReportContext(String sessionId) {
        if (sessionId == null) return "";

        return feedbackReportRepository.findBySessionId(sessionId)
                .map(report -> "\n\n[이번 통화 리포트 요약]\n"
                        + "한 줄 요약: " + nullSafe(report.getOneLineSummary()) + "\n"
                        + "문법 교정: " + nullSafe(report.getGrammarCorrections()) + "\n"
                        + "어휘 제안: " + nullSafe(report.getVocabularySuggestions()) + "\n"
                        + "발음 노트: " + nullSafe(report.getPronunciationNotes())
                )
                .orElse("");
    }

    private List<Map<String, String>> buildMessages(
            String systemPrompt,
            List<ChatMessage> history,
            String userMessage
    ) {
        // system 메시지
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // 이전 대화 히스토리 (최근 10개만 포함 — 토큰 절약)
        int start = Math.max(0, history.size() - 10);
        for (ChatMessage msg : history.subList(start, history.size())) {
            String role = "user".equals(msg.getSender()) ? "user" : "assistant";
            messages.add(Map.of("role", role, "content", msg.getContent()));
        }

        // 현재 유저 메시지
        messages.add(Map.of("role", "user", "content", userMessage));

        return messages;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
