package com.company.workflowbuilder.repository;
import com.company.workflowbuilder.entity.form.FormField;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FormFieldRepository extends JpaRepository<FormField,UUID> {
    List<FormField> findByFormVersionIdOrderByDisplayOrderAsc(UUID formVersionId);
    void deleteByFormVersionId(UUID formVersionId);
}
