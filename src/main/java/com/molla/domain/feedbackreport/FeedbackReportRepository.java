package com.molla.domain.feedbackreport;

import com.molla.domain.callsession.CallSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedbackReportRepository extends JpaRepository<FeedbackReport, String> {

    Optional<FeedbackReport> findBySessionId(String sessionId);

    boolean existsBySessionId(String sessionId);

    /**
     * 유저의 리포트 목록 — call_sessions 조인으로 userId 기준 조회, 최신순
     */
    @Query("""
            SELECT r FROM FeedbackReport r
            JOIN CallSession c ON r.sessionId = c.id
            WHERE c.userId = :userId
              AND c.status = 'completed'
            ORDER BY r.createdAt DESC
            """)
    List<FeedbackReport> findAllByUserId(@Param("userId") String userId);

    /**
     * 특정 세션의 리포트 — 본인 세션인지 동시에 확인
     */
    @Query("""
            SELECT r FROM FeedbackReport r
            JOIN CallSession c ON r.sessionId = c.id
            WHERE r.sessionId = :sessionId
              AND c.userId = :userId
            """)
    Optional<FeedbackReport> findBySessionIdAndUserId(
            @Param("sessionId") String sessionId,
            @Param("userId") String userId
    );
}
