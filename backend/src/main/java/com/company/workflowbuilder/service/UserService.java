package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.UserCreateRequest;
import com.company.workflowbuilder.dto.request.UserUpdateRequest;
import com.company.workflowbuilder.dto.response.UserResponse;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.workflow.WorkflowStatus;
import com.company.workflowbuilder.exception.DuplicateResourceException;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.mapper.UserMapper;
import com.company.workflowbuilder.repository.UserRepository;
import com.company.workflowbuilder.repository.BusinessModuleRepository;
import com.company.workflowbuilder.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final BusinessModuleRepository moduleRepository;
    private final WorkflowRepository workflowRepository;

    @Transactional(readOnly = true)
    public Page<com.company.workflowbuilder.dto.response.UserListItemResponse> getAllUsers(
            String keyword, String jobTitle, com.company.workflowbuilder.entity.user.SystemRole role, Pageable pageable) {
        
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));

            if (StringUtils.hasText(keyword)) {
                String likeKw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("displayName")), likeKw),
                    cb.like(cb.lower(root.get("email")), likeKw)
                ));
            }
            if (StringUtils.hasText(jobTitle)) {
                predicates.add(cb.equal(root.get("jobTitle"), jobTitle));
            }
            if (role != null) {
                predicates.add(cb.isMember(role, root.get("systemRoles")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(spec, pageable).map(userMapper::toListItemResponse);
    }

    @Transactional(readOnly = true)
    public List<com.company.workflowbuilder.dto.response.UserDropdownResponse> getDropdownUsers(UUID excludeId) {
        List<User> users = excludeId != null 
            ? userRepository.findByActiveTrueAndIdNot(excludeId)
            : userRepository.findByActiveTrue();
        return users.stream().map(userMapper::toDropdownResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = findUserOrThrow(id);
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getActiveUsers() {
        return userMapper.toResponseList(userRepository.findByActiveTrue());
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        Set<String> moduleCodes = validateModuleCodes(request.getSystemRoles(), request.getModuleCodes());
        // Check duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "User with email '" + request.getEmail() + "' already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .jobTitle(request.getJobTitle())
                .dataSource("MANUAL")
                .active(true)
                .systemRoles(request.getSystemRoles() != null
                        ? request.getSystemRoles()
                        : new HashSet<>())
                .moduleCodes(moduleCodes)
                .build();

        // Set manager if provided
        if (request.getManagerId() != null) {
            User manager = findUserOrThrow(request.getManagerId());
            user.setManager(manager);
        }

        User saved = userRepository.save(user);
        log.info("Created user: {} ({})", saved.getDisplayName(), saved.getEmail());
        return userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UserUpdateRequest request) {
        User user = findUserOrThrow(id);
        Set<String> moduleCodes = validateModuleCodes(request.getSystemRoles(), request.getModuleCodes());
        Set<String> removedModules = new HashSet<>(user.getModuleCodes());
        removedModules.removeAll(moduleCodes);
        for (String code : removedModules) {
            if (workflowRepository.existsByOwnerIdAndModuleAndStatusNotIn(id, code,
                    List.of(WorkflowStatus.ARCHIVED, WorkflowStatus.DELETED))) {
                throw new IllegalStateException("Không thể gỡ module " + code
                        + " khi người dùng còn sở hữu workflow đang hoạt động");
            }
        }

        user.setDisplayName(request.getDisplayName());
        user.setJobTitle(request.getJobTitle());

        // Update manager
        if (request.getManagerId() != null) {
            if (id.equals(request.getManagerId())) {
                throw new IllegalArgumentException("User cannot be their own manager");
            }
            User manager = findUserOrThrow(request.getManagerId());
            user.setManager(manager);
        } else {
            user.setManager(null);
        }

        // Update system roles if provided
        if (request.getSystemRoles() != null) {
            user.setSystemRoles(request.getSystemRoles());
        }
        user.setModuleCodes(moduleCodes);

        // Reset password if provided
        if (StringUtils.hasText(request.getNewPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            user.setAuthVersion(user.getAuthVersion() + 1);
        }

        User saved = userRepository.save(user);
        log.info("Updated user: {} ({})", saved.getDisplayName(), saved.getEmail());
        return userMapper.toResponse(saved);
    }

    @Transactional
    public void deactivateUser(UUID id) {
        User user = findUserOrThrow(id);
        user.setActive(false);
        userRepository.save(user);
        log.info("Deactivated user: {} ({})", user.getDisplayName(), user.getEmail());
    }

    @Transactional
    public void activateUser(UUID id) {
        User user = findUserOrThrow(id);
        user.setActive(true);
        userRepository.save(user);
        log.info("Activated user: {} ({})", user.getDisplayName(), user.getEmail());
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private Set<String> validateModuleCodes(Set<SystemRole> roles, Set<String> requestedCodes) {
        Set<String> codes = requestedCodes == null ? new HashSet<>() : requestedCodes.stream()
                .filter(java.util.Objects::nonNull).map(value -> value.trim().toUpperCase())
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        boolean admin = roles != null && roles.contains(SystemRole.ADMIN);
        if (!admin && codes.isEmpty()) {
            throw new IllegalArgumentException("User không phải Admin phải thuộc ít nhất một module");
        }
        long validCount = moduleRepository.findByCodeInAndActiveTrueOrderBySortOrderAscNameAsc(codes).size();
        if (validCount != codes.size()) {
            throw new IllegalArgumentException("Danh sách module có mã không tồn tại hoặc đã ngừng hoạt động");
        }
        return codes;
    }
}
