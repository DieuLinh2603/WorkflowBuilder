package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.entity.runtime.WorkflowInstance;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.repository.UserGroupRepository;
import com.company.workflowbuilder.repository.UserRepository;
import com.company.workflowbuilder.repository.InstanceStepLogRepository;
import com.company.workflowbuilder.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowActorResolver {
    private final UserRepository users;
    private final UserGroupRepository groups;
    private final CurrentUserService currentUser;
    private final InstanceStepLogRepository logs;

    public List<User> resolve(WorkflowInstance instance, Map<String, Object> config) {
        String mode = Objects.toString(config.get("approverMode"), "FIXED_USER");
        LinkedHashMap<UUID, User> resolved = new LinkedHashMap<>();
        switch (mode) {
            case "ROLE_BASED" -> users.findByJobTitleIgnoreCaseAndActiveTrue(
                    Objects.toString(config.get("actorRole"), "")).forEach(user -> addActive(resolved, user));
            case "DYNAMIC" -> resolveDynamic(instance, config, resolved);
            default -> {
                for (Object raw : (Collection<?>) config.getOrDefault("actorUserIds", List.of()))
                    users.findById(UUID.fromString(raw.toString())).ifPresent(user -> addActive(resolved, user));
                for (Object raw : (Collection<?>) config.getOrDefault("assignmentGroupIds", List.of()))
                    groups.findById(UUID.fromString(raw.toString()))
                            .ifPresent(group -> group.getMembers().forEach(user -> addActive(resolved, user)));
            }
        }
        return new ArrayList<>(resolved.values());
    }

    private void resolveDynamic(WorkflowInstance instance, Map<String, Object> config, Map<UUID, User> resolved) {
        String source = Objects.toString(config.get("dynamicActorSource"), "REQUEST_CREATOR_MANAGER");
        if ("REQUEST_CREATOR".equals(source))
            addActive(resolved, instance.getCreatedBy());
        else if ("REQUEST_CREATOR_MANAGER".equals(source))
            addActive(resolved, instance.getCreatedBy().getManager());
        else if ("PREVIOUS_ACTOR_MANAGER".equals(source))
            addActive(resolved, previousActor(instance).map(User::getManager).orElse(null));
    }

    private java.util.Optional<User> previousActor(WorkflowInstance instance) {
        try { return java.util.Optional.of(currentUser.user()); }
        catch (RuntimeException ignored) {
            var history = logs.findByInstanceIdOrderByActedAtAsc(instance.getId());
            for (int index = history.size() - 1; index >= 0; index--)
                if (history.get(index).getActor() != null) return java.util.Optional.of(history.get(index).getActor());
            return java.util.Optional.ofNullable(instance.getCreatedBy());
        }
    }

    private void addActive(Map<UUID, User> target, User user) {
        if (user != null && user.isActive())
            target.put(user.getId(), user);
    }
}
