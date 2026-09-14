package com.company.workflowbuilder.repository;
import com.company.workflowbuilder.entity.form.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FormVersionRepository extends JpaRepository<FormVersion,UUID> {
    List<FormVersion> findByFormIdOrderByVersionNumberDesc(UUID formId);
    Optional<FormVersion> findByFormIdAndStatus(UUID formId,FormStatus status);
}
