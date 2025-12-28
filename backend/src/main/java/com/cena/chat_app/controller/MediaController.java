package com.cena.chat_app.controller;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.CreateMediaMessageRequest;
import com.cena.chat_app.dto.request.RequestPresignedUrlRequest;
import com.cena.chat_app.dto.response.MessageResponse;
import com.cena.chat_app.dto.response.PresignedUrlResponse;
import com.cena.chat_app.service.MediaService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> requestPresignedUrl(@RequestBody RequestPresignedUrlRequest request) {
        return mediaService.requestPresignedUrl(request);
    }

    @PostMapping("/messages")
    public ApiResponse<MessageResponse> createMediaMessage(@RequestBody CreateMediaMessageRequest request) {
        return mediaService.createMediaMessage(request);
    }
}
