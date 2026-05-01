package com.molla.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthCodeRepository extends JpaRepository<AuthCode, String> {

    /**
     * 가장 최근 발급된 미인증 코드 조회.
     * 동일 번호로 여러 번 요청한 경우 최신 코드만 사용.
     */
    @Query("""
            SELECT a FROM AuthCode a
            WHERE a.phoneNumber = :phoneNumber
              AND a.isVerified = false
            ORDER BY a.createdAt DESC
            LIMIT 1
            """)
    Optional<AuthCode> findLatestUnverifiedByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /** 재발송 전 기존 코드 전체 삭제 */
    @Modifying
    @Query("DELETE FROM AuthCode a WHERE a.phoneNumber = :phoneNumber")
    void deleteAllByPhoneNumber(@Param("phoneNumber") String phoneNumber);
}
