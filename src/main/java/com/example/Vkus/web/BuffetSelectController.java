package com.example.Vkus.web;

import com.example.Vkus.repository.UserRepository;
import com.example.Vkus.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Controller
public class BuffetSelectController {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    public BuffetSelectController(CurrentUserService currentUserService, UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
    }

    @PostMapping("/buffet/select")
    @Transactional
    public String selectBuffet(@RequestParam("buffetId") Long buffetId,
                               HttpServletRequest request) {

        var user = currentUserService.getCurrentUser();
        userRepository.updateDefaultBuffet(user.getId(), buffetId);

        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/");
    }
}
