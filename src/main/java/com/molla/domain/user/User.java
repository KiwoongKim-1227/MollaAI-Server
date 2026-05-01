package com.molla.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "phone_number", nullable = false, length = 20, unique = true)
    private String phoneNumber;

    @Column(length = 50)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "is_registered", nullable = false)
    private boolean isRegistered;

    @Column(name = "english_level", length = 20)
    private String englishLevel;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "first_call_at")
    private LocalDateTime firstCallAt;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "refresh_token", length = 512)
    private String refreshToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    // ──────────────────────────────────────────────
    // 정적 팩토리
    // ──────────────────────────────────────────────

    /** 전화 수신 시 최초 생성 (비회원) */
    public static User createByPhone(String phoneNumber) {
        User user = new User();
        user.id = UUID.randomUUID().toString();
        user.phoneNumber = phoneNumber;
        user.isRegistered = false;
        user.status = "active";
        user.firstCallAt = LocalDateTime.now();
        return user;
    }

    // ──────────────────────────────────────────────
    // 비즈니스 메서드
    // ──────────────────────────────────────────────

    /** 앱 회원가입 완료 */
    public void register(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.isRegistered = true;
        this.registeredAt = LocalDateTime.now();
    }

    /** 내 정보 수정 */
    public void update(String username, String englishLevel) {
        if (username != null) this.username = username;
        if (englishLevel != null) this.englishLevel = englishLevel;
    }

    /** 회원 탈퇴 */
    public void withdraw() {
        this.status = "withdrawn";
        this.refreshToken = null;
        this.tokenExpiresAt = null;
    }

    /** Refresh Token 저장 */
    public void updateRefreshToken(String refreshToken, LocalDateTime expiresAt) {
        this.refreshToken = refreshToken;
        this.tokenExpiresAt = expiresAt;
    }

    public boolean isWithdrawn() {
        return "withdrawn".equals(this.status);
    }

    public boolean isSuspended() {
        return "suspended".equals(this.status);
    }
}
