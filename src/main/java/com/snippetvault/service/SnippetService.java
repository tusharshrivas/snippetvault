package com.snippetvault.service;

import com.snippetvault.dto.SnippetRequest;
import com.snippetvault.dto.SnippetResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SnippetService {

    SnippetResponse create(SnippetRequest request, String username);

    List<SnippetResponse> getAllForUser(String username);

    SnippetResponse getById(Long id, String username);

    SnippetResponse update(Long id, SnippetRequest request, String username);

    void delete(Long id, String username);

    Page<SnippetResponse> getPublicSnippets(Pageable pageable);

    Page<SnippetResponse> searchPublicSnippets(String keyword, String language, Pageable pageable);
}