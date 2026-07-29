package com.snippetvault.service;

import com.snippetvault.dto.SnippetRequest;
import com.snippetvault.dto.SnippetResponse;

import com.snippetvault.exception.ResourceNotFoundException;
import com.snippetvault.exception.UnauthorizedException;
import com.snippetvault.model.Snippet;
import com.snippetvault.model.User;
import com.snippetvault.repository.SnippetRepository;
import com.snippetvault.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnippetServiceTest {

    @Mock private SnippetRepository snippetRepository;
    @Mock private UserRepository    userRepository;

    @InjectMocks
    private SnippetServiceImpl snippetService;

    private User    owner;
    private User    otherUser;
    private Snippet snippet;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(1L)
                .username("tushar")
                .email("tushar@example.com")
                .password("hashed")
                .apiKey("key1")
                .build();

        otherUser = User.builder()
                .id(2L)
                .username("other")
                .email("other@example.com")
                .password("hashed")
                .apiKey("key2")
                .build();

        snippet = Snippet.builder()
                .id(10L)
                .title("Bubble Sort")
                .language("java")
                .code("void sort() {}")
                .description("Classic sort")
                .isPublic(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .user(owner)
                .build();
    }

    // -------------------------------------------------------------------------
    // create()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("create — success: saves snippet and returns response")
    void create_success_returnsResponse() {
        SnippetRequest request = SnippetRequest.builder()
                .title("Bubble Sort")
                .language("java")
                .code("void sort() {}")
                .description("Classic sort")
                .isPublic(true)
                .build();

        when(userRepository.findByUsername("tushar")).thenReturn(Optional.of(owner));
        when(snippetRepository.save(any(Snippet.class))).thenReturn(snippet);

        SnippetResponse response = snippetService.create(request, "tushar");

        assertThat(response.title()).isEqualTo("Bubble Sort");
        assertThat(response.language()).isEqualTo("java");
        assertThat(response.ownerUsername()).isEqualTo("tushar");
        verify(snippetRepository).save(any(Snippet.class));
    }

    // -------------------------------------------------------------------------
    // getAllForUser()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getAllForUser — returns list mapped to SnippetResponse")
    void getAllForUser_returnsListOfResponses() {
        when(userRepository.findByUsername("tushar")).thenReturn(Optional.of(owner));
        when(snippetRepository.findByUserOrderByCreatedAtDesc(owner))
                .thenReturn(List.of(snippet));

        List<SnippetResponse> results = snippetService.getAllForUser("tushar");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(10L);
    }

    @Test
    @DisplayName("getAllForUser — returns empty list when user has no snippets")
    void getAllForUser_noSnippets_returnsEmptyList() {
        when(userRepository.findByUsername("tushar")).thenReturn(Optional.of(owner));
        when(snippetRepository.findByUserOrderByCreatedAtDesc(owner)).thenReturn(List.of());

        List<SnippetResponse> results = snippetService.getAllForUser("tushar");

        assertThat(results).isEmpty();
    }

    // -------------------------------------------------------------------------
    // getById()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getById — success: owner can retrieve their own snippet")
    void getById_ownerCanRetrieve() {
        when(userRepository.findByUsername("tushar")).thenReturn(Optional.of(owner));
        when(snippetRepository.findByIdAndUser(10L, owner)).thenReturn(Optional.of(snippet));

        SnippetResponse response = snippetService.getById(10L, "tushar");

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.title()).isEqualTo("Bubble Sort");
    }

    @Test
    @DisplayName("getById — throws ResourceNotFoundException when snippet not owned by caller")
    void getById_notOwner_throwsResourceNotFoundException() {
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(snippetRepository.findByIdAndUser(10L, otherUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> snippetService.getById(10L, "other"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getById — throws ResourceNotFoundException for non-existent ID")
    void getById_nonExistentId_throwsResourceNotFoundException() {
        when(userRepository.findByUsername("tushar")).thenReturn(Optional.of(owner));
        when(snippetRepository.findByIdAndUser(999L, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> snippetService.getById(999L, "tushar"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // update()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("update — success: owner can update their snippet")
    void update_success_ownerCanUpdate() {
        SnippetRequest updateRequest = SnippetRequest.builder()
                .title("Quick Sort")
                .language("java")
                .code("void quickSort() {}")
                .description("Faster sort")
                .isPublic(false)
                .build();

        when(userRepository.findByUsername("tushar")).thenReturn(Optional.of(owner));
        when(snippetRepository.findById(10L)).thenReturn(Optional.of(snippet));

        SnippetResponse response = snippetService.update(10L, updateRequest, "tushar");

        assertThat(response.title()).isEqualTo("Quick Sort");
        assertThat(response.language()).isEqualTo("java");
        assertThat(response.isPublic()).isFalse();
        // No explicit save() call — Hibernate dirty checking handles the UPDATE
        verify(snippetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update — throws UnauthorizedException when caller does not own snippet")
    void update_notOwner_throwsUnauthorizedException() {
        SnippetRequest updateRequest = SnippetRequest.builder()
                .title("Hacked Title")
                .language("java")
                .code("malicious()")
                .isPublic(true)
                .build();

        // snippet belongs to owner (id=1), but otherUser (id=2) is trying to update it
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(snippetRepository.findById(10L)).thenReturn(Optional.of(snippet));

        assertThatThrownBy(() -> snippetService.update(10L, updateRequest, "other"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("update — throws ResourceNotFoundException for non-existent snippet")
    void update_nonExistentSnippet_throwsResourceNotFoundException() {
        SnippetRequest updateRequest = SnippetRequest.builder()
                .title("Title")
                .language("java")
                .code("code()")
                .isPublic(true)
                .build();

        when(userRepository.findByUsername("tushar")).thenReturn(Optional.of(owner));
        when(snippetRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> snippetService.update(999L, updateRequest, "tushar"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // delete()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("delete — success: owner can delete their snippet")
    void delete_success_ownerCanDelete() {
        when(userRepository.findByUsername("tushar")).thenReturn(Optional.of(owner));
        when(snippetRepository.findById(10L)).thenReturn(Optional.of(snippet));

        snippetService.delete(10L, "tushar");

        verify(snippetRepository).delete(snippet);
    }

    @Test
    @DisplayName("delete — throws UnauthorizedException when caller does not own snippet")
    void delete_notOwner_throwsUnauthorizedException() {
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(snippetRepository.findById(10L)).thenReturn(Optional.of(snippet));

        assertThatThrownBy(() -> snippetService.delete(10L, "other"))
                .isInstanceOf(UnauthorizedException.class);

        verify(snippetRepository, never()).delete(any());
    }

    // -------------------------------------------------------------------------
    // searchPublicSnippets()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("searchPublicSnippets — returns paged results matching keyword")
    void searchPublicSnippets_returnsPagedResults() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Snippet> snippetPage = new PageImpl<>(List.of(snippet), pageable, 1);

        when(snippetRepository.searchPublicSnippets("sort", "java", pageable))
                .thenReturn(snippetPage);

        Page<SnippetResponse> results = snippetService.searchPublicSnippets("sort", "java", pageable);

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).title()).isEqualTo("Bubble Sort");
    }

    @Test
    @DisplayName("searchPublicSnippets — blank keyword is normalised to null")
    void searchPublicSnippets_blankKeyword_passesNullToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        when(snippetRepository.searchPublicSnippets(null, null, pageable))
                .thenReturn(Page.empty(pageable));

        // Pass blank strings — service should normalise them to null before calling repository
        snippetService.searchPublicSnippets("   ", "   ", pageable);

        verify(snippetRepository).searchPublicSnippets(null, null, pageable);
    }
}