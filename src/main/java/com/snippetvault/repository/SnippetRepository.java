package com.snippetvault.repository;

import com.snippetvault.model.Snippet;
import com.snippetvault.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnippetRepository extends JpaRepository<Snippet, Long> {

    List<Snippet> findByUserOrderByCreatedAtDesc(User user);

    Optional<Snippet> findByIdAndUser(Long id, User user);

    Page<Snippet> findByIsPublicTrue(Pageable pageable);

    @Query("""
            SELECT s FROM Snippet s
            WHERE s.isPublic = true
              AND (:language IS NULL OR LOWER(s.language) = LOWER(:language))
              AND (:keyword IS NULL OR
                   LOWER(s.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY s.createdAt DESC
            """)
    Page<Snippet> searchPublicSnippets(
            @Param("keyword") String keyword,
            @Param("language") String language,
            Pageable pageable
    );
}