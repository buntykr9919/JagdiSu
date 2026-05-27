package com.jdsu.quiz.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public AuthService.UserResponse signup(@RequestBody AuthService.SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthService.UserResponse login(@RequestBody AuthService.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public AuthService.UserResponse googleLogin(@RequestBody AuthService.GoogleLoginRequest request) {
        return authService.googleLogin(request);
    }
}
