package com.snippetvault.controller;

import com.snippetvault.dto.SnippetRequest;
import com.snippetvault.dto.SnippetResponse;
import com.snippetvault.service.SnippetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snippets")
@RequiredArgsConstructor
@Tag(name = "Snippets", description = "Create, retrieve, update, and delete code snippets")
public class SnippetController {

    private final SnippetService snippetService;

    @PostMapping
    @Operation(
            summary = "Create a snippet",
            description = "Creates a new snippet owned by the authenticated user",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Snippet created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<SnippetResponse> create(
            @Valid @RequestBody SnippetRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        SnippetResponse response = snippetService.create(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
            summary = "Get all my snippets",
            description = "Returns all snippets owned by the authenticated user, newest first",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<List<SnippetResponse>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(snippetService.getAllForUser(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a snippet by ID",
            description = "Returns a specific snippet — only accessible by its owner",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Snippet found"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Snippet not found or not owned by caller")
    })
    public ResponseEntity<SnippetResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(snippetService.getById(id, userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a snippet",
            description = "Replaces all fields of an existing snippet. Caller must be the owner.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Snippet updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Caller does not own this snippet"),
            @ApiResponse(responseCode = "404", description = "Snippet not found")
    })
    public ResponseEntity<SnippetResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SnippetRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(snippetService.update(id, request, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a snippet",
            description = "Permanently deletes a snippet. Caller must be the owner.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Snippet deleted"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Caller does not own this snippet"),
            @ApiResponse(responseCode = "404", description = "Snippet not found")
    })
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        snippetService.delete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public")
    @Operation(
            summary = "Browse public snippets",
            description = "Paginated list of all public snippets. No authentication required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page returned")
    })
    public ResponseEntity<Page<SnippetResponse>> getPublic(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 50)")    @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(snippetService.getPublicSnippets(pageable));
    }

    @GetMapping("/search")
    @Operation(
            summary = "Search public snippets",
            description = "Search public snippets by keyword and/or language. No auth required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results returned")
    })
    public ResponseEntity<Page<SnippetResponse>> search(
            @Parameter(description = "Keyword to search in title and description")
            @RequestParam(required = false) String q,

            @Parameter(description = "Filter by programming language (e.g. java, python)")
            @RequestParam(required = false) String language,

            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 50)")    @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(snippetService.searchPublicSnippets(q, language, pageable));
    }
}