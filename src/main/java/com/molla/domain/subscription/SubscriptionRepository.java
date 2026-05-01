package com.molla.domain.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

    /**
     * 유저의 현재 활성 구독 조회.
     * active 상태이고 만료되지 않은 구독 중 가장 최근 것.
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.userId = :userId
              AND s.status = 'active'
              AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP)
            ORDER BY s.startedAt DESC
            LIMIT 1
            """)
    Optional<Subscription> findActiveByUserId(@Param("userId") String userId);

    /** 이미 활성 구독이 있는지 확인 */
    @Query("""
            SELECT COUNT(s) > 0 FROM Subscription s
            WHERE s.userId = :userId
              AND s.status = 'active'
              AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP)
            """)
    boolean existsActiveByUserId(@Param("userId") String userId);
}
