package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.AudienceRequest;
import com.company.workflowbuilder.entity.user.*;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.exception.*;
import com.company.workflowbuilder.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GroupAudienceService {
    private final UserGroupRepository groups;
    private final UserRepository users;
    private final WorkflowRepository workflows;
    private final WorkflowAudienceRepository audiences;
    private final CurrentUserService current;
    private final WorkflowAuthorizationService authorization;

    @Transactional
    public UserGroup createGroup(String name) {
        current.requireAdmin();
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Group name is required");
        if (groups.existsByNameIgnoreCase(name))
            throw new DuplicateResourceException("Group name already exists: " + name);
        return groups.save(UserGroup.builder().name(name).build());
    }

    @Transactional
    public UserGroup addMember(UUID groupId, UUID userId) {
        current.requireAdmin();
        UserGroup g = group(groupId);
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (!user.isActive())
            throw new IllegalArgumentException("Không thể thêm tài khoản đã bị vô hiệu hóa vào group");
        g.getMembers().add(user);
        return groups.save(g);
    }

    @Transactional
    public UserGroup removeMember(UUID groupId, UUID userId) {
        current.requireAdmin();
        UserGroup g = group(groupId);
        g.getMembers().removeIf(user -> user.getId().equals(userId));
        return groups.save(g);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listGroups() {
        return groups.findAll().stream().sorted(Comparator.comparing(UserGroup::getName, String.CASE_INSENSITIVE_ORDER))
                .map(g -> Map.<String, Object>of("id", g.getId(), "name", g.getName(), "memberCount",
                        g.getMembers().size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> groupDetail(UUID id) {
        UserGroup g = group(id);
        List<Map<String, Object>> members = g.getMembers().stream()
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(this::memberResponse).toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", g.getId());
        response.put("name", g.getName());
        response.put("memberCount", members.size());
        response.put("members", members);
        return response;
    }

    @Transactional
    public List<WorkflowAudience> replaceAudience(UUID workflowId, List<AudienceRequest> requests) {
        Workflow w = workflow(workflowId);
        authorization.requireOwnerOrAdmin(w);
        audiences.deleteByWorkflowId(workflowId);
        List<WorkflowAudience> result = new ArrayList<>();
        for (AudienceRequest r : requests)
            result.add(audiences.save(WorkflowAudience.builder().workflow(w).subjectType(r.getSubjectType())
                    .subjectValue(r.getSubjectValue()).build()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> audience(UUID workflowId) {
        Workflow w = workflow(workflowId);
        authorization.requireView(w);
        return audiences.findByWorkflowId(workflowId).stream().map(a -> Map.<String, Object>of("id", a.getId(),
                "subjectType", a.getSubjectType(), "subjectValue", a.getSubjectValue())).toList();
    }

    private Workflow workflow(UUID id) {
        return workflows.findById(id).orElseThrow(() -> new ResourceNotFoundException("Workflow", "id", id));
    }

    private UserGroup group(UUID id) {
        return groups.findById(id).orElseThrow(() -> new ResourceNotFoundException("Group", "id", id));
    }

    private Map<String, Object> memberResponse(User user) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", user.getId());
        value.put("displayName", user.getDisplayName());
        value.put("email", user.getEmail());
        value.put("jobTitle", user.getJobTitle());
        return value;
    }
}
