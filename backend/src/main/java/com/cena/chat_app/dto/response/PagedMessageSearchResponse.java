package com.cena.chat_app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedMessageSearchResponse {
    private List<MessageSearchResponse> messages;
    private int page;
    private int size;
    private long totalElements;
    private boolean hasNext;
}
