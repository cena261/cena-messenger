package com.cena.chat_app.controller;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.response.UserProfileResponse;
import com.cena.chat_app.dto.response.UserSearchResponse;
import com.cena.chat_app.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getCurrentUser() {
        return userService.getCurrentUserProfile();
    }

    @GetMapping("/search")
    public ApiResponse<UserSearchResponse> searchUser(@RequestParam String query) {
        return userService.searchUser(query);
    }
}
