package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.UserRepository;
import com.company.workflowbuilder.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final UserRepository userRepository;

    public CustomUserDetails principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new AccessDeniedException("Authentication is required");
        }
        return details;
    }

    public UUID id() { return principal().getId(); }

    public User user() {
        return userRepository.findById(id())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id()));
    }

    public boolean hasRole(SystemRole role) {
        return principal().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.name()));
    }

    public void requireAdmin() {
        if (!hasRole(SystemRole.ADMIN)) throw new AccessDeniedException("Admin role is required");
    }
}
