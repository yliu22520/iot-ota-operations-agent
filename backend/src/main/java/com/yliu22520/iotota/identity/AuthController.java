package com.yliu22520.iotota.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final java.time.Clock clock;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          LoginAttemptLimiter loginAttemptLimiter,
                          java.time.Clock clock) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.clock = clock;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        String clientIp = httpRequest.getRemoteAddr();
        if (loginAttemptLimiter.isBlocked(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(problem("LOGIN_RATE_LIMITED", "登录失败次数过多，请稍后再试", HttpStatus.TOO_MANY_REQUESTS));
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            httpRequest.getSession(true).setAttribute(SessionLifetimeFilter.AUTHENTICATED_AT,
                    java.time.Instant.now(clock).toString());
            loginAttemptLimiter.clear(clientIp);
            return ResponseEntity.ok(new AuthResponse(authentication.getName(), "OPERATOR", true));
        } catch (BadCredentialsException exception) {
            loginAttemptLimiter.registerFailure(clientIp);
            if (loginAttemptLimiter.isBlocked(clientIp)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(problem("LOGIN_RATE_LIMITED", "登录失败次数过多，请稍后再试", HttpStatus.TOO_MANY_REQUESTS));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(problem("INVALID_CREDENTIALS", "演示运维账号或密码不正确", HttpStatus.UNAUTHORIZED));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(problem("AUTHENTICATION_FAILED", "无法完成登录", HttpStatus.UNAUTHORIZED));
        }
    }

    @GetMapping("/me")
    public AuthResponse me(Authentication authentication) {
        return new AuthResponse(authentication.getName(), "OPERATOR", true);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(String username, String password) {
    }

    public record CsrfResponse(String token) {
    }

    public record AuthResponse(String username, String role, boolean authenticated) {
    }

    private static ProblemDetail problem(String code, String message, HttpStatus status) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setProperty("code", code);
        return problem;
    }
}
