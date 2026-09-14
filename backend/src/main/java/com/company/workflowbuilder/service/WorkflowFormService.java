package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.form.*;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class WorkflowFormService {
    private final WorkflowRepository workflows;
    private final WorkflowStepRepository steps;
    private final WorkflowConnectionRepository connections;
    private final FormVersionRepository versions;
    private final FormFieldRepository fields;
    private final WorkflowAuthorizationService authorization;
    private final FormService formService;

    @Transactional
    public Map<String,Object> bind(UUID workflowId,UUID formVersionId) {
        Workflow workflow=workflow(workflowId); authorization.requireEdit(workflow);
        if(workflow.getStatus()!=WorkflowStatus.DRAFT) throw new IllegalStateException("Only a DRAFT workflow can change form");
        FormVersion version=versions.findById(formVersionId).orElseThrow(()->new ResourceNotFoundException("FormVersion","id",formVersionId));
        if(version.getStatus()!=FormStatus.PUBLISHED) throw new IllegalArgumentException("Workflow requires a published form version");
        Set<String> available=new HashSet<>(); fields.findByFormVersionIdOrderByDisplayOrderAsc(version.getId()).forEach(f->available.add(f.getFieldKey()));
        Set<String> missing=new TreeSet<>();
        for(WorkflowConnection connection:connections.findByWorkflowId(workflowId))
            connection.getClauses().stream().filter(c->c.getFieldKey()!=null&&!c.getFieldKey().isBlank()&&!available.contains(c.getFieldKey())).forEach(c->missing.add(c.getFieldKey()));
        // Step configs store field references as JSON strings. Preserve the old schema until all references are remapped.
        FormVersion old=workflow.getFormVersion();
        if(old!=null) for(FormField field:fields.findByFormVersionIdOrderByDisplayOrderAsc(old.getId()))
            if(!available.contains(field.getFieldKey())&&steps.findByWorkflowIdOrderByPositionXAsc(workflowId).stream()
                    .anyMatch(step->step.getConfigJson()!=null&&step.getConfigJson().contains("\""+field.getFieldKey()+"\"")))
                missing.add(field.getFieldKey());
        if(!missing.isEmpty()) throw new IllegalArgumentException("Form is missing workflow fields: "+String.join(", ",missing));
        workflow.setFormVersion(version); workflows.save(workflow);
        return formService.versionViewById(version.getId());
    }

    @Transactional(readOnly=true)
    public Map<String,Object> submissionDefinition(UUID workflowId) {
        Workflow workflow=workflow(workflowId);
        if(workflow.getFormVersion()==null) throw new IllegalStateException("Workflow has no form");
        Map<String,Object> out=new LinkedHashMap<>();out.put("workflowId",workflow.getId());out.put("workflowName",workflow.getName());
        out.put("workflowVersion",workflow.getVersion());out.put("form",formService.versionViewById(workflow.getFormVersion().getId()));return out;
    }
    private Workflow workflow(UUID id){return workflows.findById(id).orElseThrow(()->new ResourceNotFoundException("Workflow","id",id));}
}
