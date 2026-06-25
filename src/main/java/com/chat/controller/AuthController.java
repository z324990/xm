package com.chat.controller;

import com.chat.dto.ApiResult;
import com.chat.model.User;
import com.chat.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResult<?> register(@RequestBody RegisterRequest request, HttpSession session) {
        try {
            User user = userService.register(request.username, request.password, request.nickname);
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            return ApiResult.success(Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "nickname", user.getNickname()
            ));
        } catch (RuntimeException e) {
            return ApiResult.error(400, e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResult<?> login(@RequestBody LoginRequest request, HttpSession session) {
        try {
            User user = userService.login(request.username, request.password);
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            return ApiResult.success(Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "nickname", user.getNickname()
            ));
        } catch (RuntimeException e) {
            return ApiResult.error(401, e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ApiResult<?> logout(HttpSession session) {
        session.invalidate();
        return ApiResult.success(null);
    }

    @GetMapping("/me")
    public ApiResult<?> me(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ApiResult.error(401, "未登录");
        }
        return userService.findById(userId)
                .map(user -> ApiResult.success(Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "nickname", user.getNickname()
                )))
                .orElse(ApiResult.error(404, "用户不存在"));
    }

    public static class RegisterRequest {
        @NotBlank
        public String username;
        @NotBlank
        public String password;
        public String nickname;
    }

    public static class LoginRequest {
        @NotBlank
        public String username;
        @NotBlank
        public String password;
    }
}
