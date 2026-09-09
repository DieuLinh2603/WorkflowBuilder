package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    List<User> findByJobTitle(String jobTitle);

    List<User> findByJobTitleIgnoreCaseAndActiveTrue(String jobTitle);

    List<User> findByActiveTrue();

    List<User> findByActiveTrueAndIdNot(UUID id);

    @Query("SELECT u FROM User u WHERE u.active = true AND " +
            "(LOWER(u.displayName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    Page<User> findByActiveTrue(Pageable pageable);

    List<User> findDistinctBySystemRolesContainingAndActiveTrue(
            com.company.workflowbuilder.entity.user.SystemRole role);
}
