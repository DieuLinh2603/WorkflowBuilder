package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.user.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {
    boolean existsByNameIgnoreCase(String name);

    List<UserGroup> findByMembersId(UUID userId);
}
