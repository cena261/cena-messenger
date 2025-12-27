package com.cena.chat_app.controller;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.CreateDirectConversationRequest;
import com.cena.chat_app.dto.request.CreateGroupConversationRequest;
import com.cena.chat_app.dto.response.ConversationResponse;
import com.cena.chat_app.dto.response.UnreadUpdateResponse;
import com.cena.chat_app.service.ConversationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/direct")
    public ApiResponse<ConversationResponse> createDirectConversation(@RequestBody CreateDirectConversationRequest request) {
        return conversationService.createDirectConversation(request);
    }

    @PostMapping("/group")
    public ApiResponse<ConversationResponse> createGroupConversation(@RequestBody CreateGroupConversationRequest request) {
        return conversationService.createGroupConversation(request);
    }

    @GetMapping
    public ApiResponse<List<ConversationResponse>> getConversations() {
        return conversationService.getConversations();
    }

    @PostMapping("/{conversationId}/read")
    public ApiResponse<UnreadUpdateResponse> markConversationAsRead(@PathVariable String conversationId) {
        return conversationService.markConversationAsRead(conversationId);
    }
}
