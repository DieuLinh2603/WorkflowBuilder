package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.ChangePasswordRequest;
import com.company.workflowbuilder.dto.request.ForgotPasswordRequest;
import com.company.workflowbuilder.dto.request.ResetPasswordRequest;
import com.company.workflowbuilder.entity.user.PasswordResetToken;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.repository.PasswordResetTokenRepository;
import com.company.workflowbuilder.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {
    private static final String GENERIC_FORGOT_MESSAGE = "Nếu email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi.";

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUser;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.password-reset.expiration-minutes:30}")
    private long expirationMinutes;
    @Value("${app.password-reset.frontend-url:http://localhost:5174/reset-password}")
    private String frontendUrl;
    @Value("${app.password-reset.expose-development-link:false}")
    private boolean exposeDevelopmentLink;

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUser.user();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash()))
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng");
        validateNewPassword(request.getNewPassword(), request.getConfirmPassword(), user);
        applyPassword(user, request.getNewPassword());
        resetTokens.deleteByUserId(user.getId());
    }

    @Transactional
    public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", GENERIC_FORGOT_MESSAGE);
        users.findByEmailIgnoreCase(request.getEmail().trim()).filter(User::isActive).ifPresent(user -> {
            resetTokens.deleteByUserId(user.getId());
            String rawToken = newToken();
            resetTokens.save(PasswordResetToken.builder().user(user).tokenHash(hash(rawToken))
                    .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes)).build());
            String resetUrl = frontendUrl + "?token=" + rawToken;
            if (smtpConfigured())
                sendResetEmail(user, resetUrl);
            else {
                log.warn("SMTP chưa được cấu hình. Link reset mật khẩu development cho {}: {}", user.getEmail(),
                        resetUrl);
                if (exposeDevelopmentLink)
                    response.put("developmentResetUrl", resetUrl);
            }
        });
        return response;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = resetTokens.findByTokenHash(hash(request.getToken()))
                .orElseThrow(
                        () -> new IllegalArgumentException("Link đặt lại mật khẩu không hợp lệ hoặc đã được sử dụng"));
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("Link đặt lại mật khẩu đã hết hạn hoặc đã được sử dụng");
        User user = token.getUser();
        if (!user.isActive())
            throw new IllegalStateException("Tài khoản đã bị vô hiệu hóa");
        validateNewPassword(request.getNewPassword(), request.getConfirmPassword(), user);
        token.setUsedAt(LocalDateTime.now());
        applyPassword(user, request.getNewPassword());
    }

    private void validateNewPassword(String password, String confirmation, User user) {
        if (!password.equals(confirmation))
            throw new IllegalArgumentException("Xác nhận mật khẩu không khớp");
        if (passwordEncoder.matches(password, user.getPasswordHash()))
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại");
    }

    private void applyPassword(User user, String password) {
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setAuthVersion(user.getAuthVersion() + 1);
        users.save(user);
    }

    private boolean smtpConfigured() {
        return environment.getProperty("spring.mail.host") != null && mailSenders.getIfAvailable() != null;
    }

    private void sendResetEmail(User user, String resetUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Đặt lại mật khẩu WorkflowBuilder");
        message.setText("Xin chào " + user.getDisplayName() + ",\n\n"
                + "Mở liên kết sau để đặt lại mật khẩu. Liên kết có hiệu lực trong " + expirationMinutes + " phút:\n"
                + resetUrl + "\n\nNếu bạn không yêu cầu thao tác này, hãy bỏ qua email.");
        mailSenders.getObject().send(message);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể xử lý reset token", exception);
        }
    }
}
