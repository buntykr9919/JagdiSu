package com.jdsu.quiz.auth;

import com.jdsu.quiz.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public AuthService.UserResponse signup(@RequestBody AuthService.SignupRequest request, HttpServletRequest servletRequest) {
        return authService.signup(request, deviceContext(servletRequest, null, null));
    }

    @PostMapping("/login")
    public AuthService.UserResponse login(
            @RequestBody AuthService.LoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Name", required = false) String deviceName,
            HttpServletRequest servletRequest
    ) {
        return authService.login(request, deviceContext(servletRequest, deviceId, deviceName));
    }

    @PostMapping("/google")
    public AuthService.UserResponse googleLogin(
            @RequestBody AuthService.GoogleLoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Name", required = false) String deviceName,
            HttpServletRequest servletRequest
    ) {
        return authService.googleLogin(request, deviceContext(servletRequest, deviceId, deviceName));
    }

    @PostMapping("/refresh")
    public AuthService.UserResponse refresh(
            @RequestBody AuthService.RefreshRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Name", required = false) String deviceName,
            HttpServletRequest servletRequest
    ) {
        return authService.refresh(request, deviceContext(servletRequest, deviceId, deviceName));
    }

    @PostMapping("/logout")
    public AuthService.VerificationResponse logout(@RequestBody AuthService.RefreshRequest request) {
        return authService.logout(request);
    }

    @PostMapping("/email/request")
    public AuthService.VerificationResponse requestEmailVerification(@RequestBody AuthService.EmailVerificationRequest request) {
        return authService.requestEmailVerification(request);
    }

    @PostMapping("/email/verify")
    public AuthService.VerificationResponse verifyEmail(@RequestBody AuthService.VerifyTokenRequest request) {
        return authService.verifyEmail(request);
    }

    @PostMapping("/otp/send")
    public AuthService.VerificationResponse sendOtp(@RequestBody AuthService.OtpSendRequest request) {
        return authService.sendOtp(request);
    }

    @PostMapping("/otp/verify")
    public AuthService.UserResponse verifyOtp(
            @RequestBody AuthService.OtpVerifyRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Name", required = false) String deviceName,
            HttpServletRequest servletRequest
    ) {
        return authService.verifyOtp(request, deviceContext(servletRequest, deviceId, deviceName));
    }

    @PostMapping("/forgot-password")
    public AuthService.VerificationResponse forgotPassword(@RequestBody AuthService.ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public AuthService.VerificationResponse resetPassword(@RequestBody AuthService.ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @GetMapping("/sessions")
    public List<AuthService.DeviceSessionResponse> sessions(@AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return authService.sessions(requirePrincipal(principal).userId());
    }

    @PostMapping("/sessions/{id}/revoke")
    public AuthService.VerificationResponse revokeSession(
            @AuthenticationPrincipal JwtService.JwtPrincipal principal,
            @PathVariable long id
    ) {
        return authService.revokeSession(requirePrincipal(principal).userId(), id);
    }

    private JwtService.JwtPrincipal requirePrincipal(JwtService.JwtPrincipal principal) {
        if (principal == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        return principal;
    }

    private AuthService.DeviceContext deviceContext(HttpServletRequest request, String deviceId, String deviceName) {
        return new AuthService.DeviceContext(
                deviceId,
                deviceName,
                clientIp(request),
                request.getHeader("User-Agent")
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
