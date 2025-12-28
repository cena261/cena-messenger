package com.cena.chat_app.controller;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.DeleteMessageRequest;
import com.cena.chat_app.dto.request.EditMessageRequest;
import com.cena.chat_app.dto.request.ReactionRequest;
import com.cena.chat_app.dto.request.SendMessageRequest;
import com.cena.chat_app.dto.response.MessageResponse;
import com.cena.chat_app.dto.response.MessageUpdateEventResponse;
import com.cena.chat_app.dto.response.ReactionEventResponse;
import com.cena.chat_app.service.MessageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ApiResponse<MessageResponse> sendMessage(@RequestBody SendMessageRequest request) {
        return messageService.sendMessage(request);
    }

    @GetMapping
    public ApiResponse<List<MessageResponse>> getMessages(
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return messageService.getMessages(conversationId, pageable);
    }

    @PostMapping("/reactions")
    public ApiResponse<ReactionEventResponse> toggleReaction(@RequestBody ReactionRequest request) {
        return messageService.toggleReaction(request);
    }

    @PutMapping("/{messageId}")
    public ApiResponse<MessageUpdateEventResponse> editMessage(
            @PathVariable String messageId,
            @RequestBody EditMessageRequest request) {
        request.setMessageId(messageId);
        return messageService.editMessage(request);
    }

    @DeleteMapping("/{messageId}")
    public ApiResponse<MessageUpdateEventResponse> deleteMessage(@PathVariable String messageId) {
        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .messageId(messageId)
                .build();
        return messageService.deleteMessage(request);
    }
}
