package com.cena.chat_app.controller;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.response.ConversationSearchResponse;
import com.cena.chat_app.dto.response.PagedMessageSearchResponse;
import com.cena.chat_app.service.SearchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationSearchResponse>> searchConversations(@RequestParam String query) {
        return searchService.searchConversations(query);
    }

    @GetMapping("/messages")
    public ApiResponse<PagedMessageSearchResponse> searchMessages(
            @RequestParam String conversationId,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return searchService.searchMessages(conversationId, query, pageable);
    }
}
