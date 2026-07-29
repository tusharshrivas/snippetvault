package com.snippetvault.service;

import com.snippetvault.dto.SnippetRequest;
import com.snippetvault.dto.SnippetResponse;
import com.snippetvault.exception.ResourceNotFoundException;
import com.snippetvault.exception.UnauthorizedException;
import com.snippetvault.model.Snippet;
import com.snippetvault.model.User;
import com.snippetvault.repository.SnippetRepository;
import com.snippetvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SnippetServiceImpl implements SnippetService {

    private final SnippetRepository snippetRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public SnippetResponse create(SnippetRequest request, String username) {
        User user = loadUser(username);

        Snippet snippet = Snippet.builder()
                .title(request.title())
                .language(request.language())
                .code(request.code())
                .description(request.description())
                .isPublic(request.isPublic())
                .user(user)
                .build();

        return SnippetResponse.from(snippetRepository.save(snippet));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SnippetResponse> getAllForUser(String username) {
        User user = loadUser(username);
        return snippetRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(SnippetResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SnippetResponse getById(Long id, String username) {
        User user = loadUser(username);
        Snippet snippet = snippetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Snippet", id));
        return SnippetResponse.from(snippet);
    }

    @Override
    @Transactional
    public SnippetResponse update(Long id, SnippetRequest request, String username) {
        User user = loadUser(username);

        Snippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Snippet", id));

        if (!snippet.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You do not own this snippet");
        }

        snippet.setTitle(request.title());
        snippet.setLanguage(request.language());
        snippet.setCode(request.code());
        snippet.setDescription(request.description());
        snippet.setPublic(request.isPublic());

        return SnippetResponse.from(snippet);
    }

    @Override
    @Transactional
    public void delete(Long id, String username) {
        User user = loadUser(username);

        Snippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Snippet", id));

        if (!snippet.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You do not own this snippet");
        }

        snippetRepository.delete(snippet);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SnippetResponse> getPublicSnippets(Pageable pageable) {
        return snippetRepository.findByIsPublicTrue(pageable)
                .map(SnippetResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SnippetResponse> searchPublicSnippets(String keyword, String language, Pageable pageable) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String lang = StringUtils.hasText(language) ? language.trim() : null;

        return snippetRepository.searchPublicSnippets(kw, lang, pageable)
                .map(SnippetResponse::from);
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Authenticated user not found: " + username));
    }
}