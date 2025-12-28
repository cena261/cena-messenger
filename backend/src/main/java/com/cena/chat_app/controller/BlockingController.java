package com.cena.chat_app.controller;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.BlockUserRequest;
import com.cena.chat_app.dto.response.BlockedUserResponse;
import com.cena.chat_app.service.BlockingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/blocking")
public class BlockingController {
    private final BlockingService blockingService;

    public BlockingController(BlockingService blockingService) {
        this.blockingService = blockingService;
    }

    @PostMapping("/block")
    public ApiResponse<Void> blockUser(@RequestBody BlockUserRequest request) {
        return blockingService.blockUser(request.getUserId());
    }

    @PostMapping("/unblock")
    public ApiResponse<Void> unblockUser(@RequestBody BlockUserRequest request) {
        return blockingService.unblockUser(request.getUserId());
    }

    @GetMapping("/blocked-users")
    public ApiResponse<List<BlockedUserResponse>> getBlockedUsers() {
        return blockingService.getBlockedUsers();
    }
}
