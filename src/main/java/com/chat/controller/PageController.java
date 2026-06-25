package com.chat.controller;

import com.chat.model.User;
import com.chat.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final UserService userService;

    public PageController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        if (session.getAttribute("userId") != null) {
            return "redirect:/chat";
        }
        return "index";
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if (session.getAttribute("userId") != null) {
            return "redirect:/chat";
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(HttpSession session) {
        if (session.getAttribute("userId") != null) {
            return "redirect:/chat";
        }
        return "register";
    }

    @GetMapping("/chat")
    public String chatPage(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        User user = userService.findById(userId).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("username", user.getUsername());
        return "chat";
    }
}
