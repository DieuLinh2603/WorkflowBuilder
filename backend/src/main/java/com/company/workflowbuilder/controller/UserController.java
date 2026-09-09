package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.request.UserCreateRequest;
import com.company.workflowbuilder.dto.request.UserUpdateRequest;
import com.company.workflowbuilder.dto.response.UserResponse;
import com.company.workflowbuilder.dto.response.UserListItemResponse;
import com.company.workflowbuilder.dto.response.UserDropdownResponse;
import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Operations pertaining to user management (Admin only)")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all users with optional keyword search", description = "Requires ADMIN role")
    public ResponseEntity<Page<UserListItemResponse>> getAllUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) SystemRole role,
            Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(keyword, jobTitle, role, pageable));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER')")
    @Operation(summary = "Get list of active users for dropdowns", description = "Requires ADMIN, WORKFLOW_OWNER, or EDITOR role")
    public ResponseEntity<List<UserResponse>> getActiveUsers() {
        return ResponseEntity.ok(userService.getActiveUsers());
    }

    @GetMapping("/dropdown")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get list of users for dropdowns (excluding a specific ID)", description = "Requires ADMIN role")
    public ResponseEntity<List<UserDropdownResponse>> getDropdownUsers(
            @RequestParam(required = false) UUID exclude) {
        return ResponseEntity.ok(userService.getDropdownUsers(exclude));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user by ID", description = "Requires ADMIN role")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new user", description = "Requires ADMIN role")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an existing user", description = "Requires ADMIN role")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a user", description = "Requires ADMIN role")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate a user", description = "Requires ADMIN role")
    public ResponseEntity<Void> activateUser(@PathVariable UUID id) {
        userService.activateUser(id);
        return ResponseEntity.noContent().build();
    }
}
