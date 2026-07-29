package com.snippetvault.dto;

import com.snippetvault.model.Snippet;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SnippetResponse(
        Long id,
        String title,
        String language,
        String code,
        String description,
        boolean isPublic,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String ownerUsername
) {
    public static SnippetResponse from(Snippet snippet) {
        return SnippetResponse.builder()
                .id(snippet.getId())
                .title(snippet.getTitle())
                .language(snippet.getLanguage())
                .code(snippet.getCode())
                .description(snippet.getDescription())
                .isPublic(snippet.isPublic())
                .createdAt(snippet.getCreatedAt())
                .updatedAt(snippet.getUpdatedAt())
                .ownerUsername(snippet.getUser().getUsername())
                .build();
    }
}