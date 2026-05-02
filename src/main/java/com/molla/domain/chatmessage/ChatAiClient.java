package com.molla.domain.chatmessage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.molla.common.response.ErrorCode;
import com.molla.domain.feedbackreport.FeedbackReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * username을 시스템 프롬프트에 포함해서 AI가 이름으로 호칭.
     * history에 이미 최신 유저 메시지 포함 — 별도 append 없음.
     */
    public String generateReply(String sessionId, List<ChatMessage> history, String username) {
        String reportContext = buildReportContext(sessionId);

        // username이 있으면 AI가 이름으로 호칭
        String nameContext = (username != null && !username.isBlank())
                ? "학습자의 이름은 " + username + "입니다. 대화 중 자연스럽게 이름을 불러주세요.\n"
                : "";

        String systemPrompt = "당신은 친절한 영어 학습 코치입니다. 학습자의 질문에 한국어로 답변하되, "
                + "영어 예시나 교정은 영어로 작성하세요.\n"
                + nameContext
                + reportContext;

        List<Map<String, String>> messages = buildMessages(systemPrompt, history);

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

            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.error("ChatAI 응답에 choices 없음. 응답: {}", response);
                throw new ChatMessageException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                log.error("ChatAI 응답에 content 없음. 응답: {}", response);
                throw new ChatMessageException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            return content.asText();

        } catch (ChatMessageException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 채팅 응답 생성 실패: {}", e.getMessage(), e);
            throw new ChatMessageException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

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
            List<ChatMessage> history
    ) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        int start = Math.max(0, history.size() - 10);
        for (ChatMessage msg : history.subList(start, history.size())) {
            String role = "user".equals(msg.getSender()) ? "user" : "assistant";
            messages.add(Map.of("role", role, "content", msg.getContent()));
        }

        return messages;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
