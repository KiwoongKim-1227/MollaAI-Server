package com.molla.domain.user;

import com.molla.common.response.ErrorCode;
import com.molla.controller.dto.user.RegisterRequest;
import com.molla.controller.dto.user.UpdateUserRequest;
import com.molla.controller.dto.user.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ──────────────────────────────────────────────
    // 내 정보 조회
    // ──────────────────────────────────────────────

    public UserResponse getMe(String userId) {
        User user = findActiveUser(userId);
        return UserResponse.from(user);
    }

    // ──────────────────────────────────────────────
    // 앱 회원가입
    // ──────────────────────────────────────────────

    @Transactional
    public UserResponse register(String userId, RegisterRequest request) {
        User user = findActiveUser(userId);

        if (user.isRegistered()) {
            throw new UserException(ErrorCode.USER_ALREADY_REGISTERED);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        user.register(request.username(), encodedPassword);

        log.info("회원가입 완료 — userId: {}", userId);
        return UserResponse.from(user);
    }

    // ──────────────────────────────────────────────
    // 내 정보 수정
    // ──────────────────────────────────────────────

    @Transactional
    public UserResponse updateMe(String userId, UpdateUserRequest request) {
        User user = findActiveUser(userId);
        user.update(request.username(), request.englishLevel());

        log.info("정보 수정 완료 — userId: {}", userId);
        return UserResponse.from(user);
    }

    // ──────────────────────────────────────────────
    // 회원 탈퇴
    // ──────────────────────────────────────────────

    @Transactional
    public void withdraw(String userId) {
        User user = findActiveUser(userId);
        user.withdraw();

        log.info("회원 탈퇴 완료 — userId: {}", userId);
    }

    // ──────────────────────────────────────────────
    // 내부 유틸
    // ──────────────────────────────────────────────

    private User findActiveUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            throw new UserException(ErrorCode.USER_NOT_FOUND);
        }

        if (user.isSuspended()) {
            throw new UserException(ErrorCode.USER_SUSPENDED);
        }

        return user;
    }
}
