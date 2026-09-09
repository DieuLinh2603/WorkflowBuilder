package com.company.workflowbuilder.service.data;

import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.repository.*;
import com.company.workflowbuilder.service.CurrentUserService;
import com.company.workflowbuilder.service.runtime.WorkflowJsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class BatchRecordMigrationService {
    private final WorkflowInstanceRepository instances; private final WorkflowBatchRecordRepository records;
    private final WorkflowStepRepository steps; private final WorkflowJsonCodec codec; private final CurrentUserService currentUser;

    @Transactional @SuppressWarnings("unchecked")
    public Map<String,Object> migrate() {
        currentUser.requireAdmin(); int migratedInstances=0,migratedRecords=0;
        for (WorkflowInstance instance:instances.findAll()) {
            if(instance.getBatchId()==null||records.countByInstanceId(instance.getId())>0) continue;
            Object raw=codec.snapshot(instance.getFieldSnapshot()).get("records"); if(!(raw instanceof List<?> list)) continue;
            for(Object item:list) {
                if(!(item instanceof Map<?,?>)) continue; Map<String,Object> state=(Map<String,Object>)item;
                int row=state.get("rowNumber") instanceof Number n?n.intValue():migratedRecords+1;
                Object fields=state.get("fields"),stepId=state.get("currentStepId"),acted=state.get("humanActionAt");
                records.save(WorkflowBatchRecord.builder().instance(instance).rowNumber(row)
                        .revision(state.get("revision") instanceof Number n?n.intValue():1)
                        .businessKey(Objects.toString(state.get("businessKey"),null)).checksum(Objects.toString(state.get("checksum"),null))
                        .payloadJson(codec.write(fields instanceof Map<?,?> m?m:Map.of())).stateJson(codec.write(state))
                        .status(Objects.toString(state.get("status"),"RUNNING"))
                        .currentStep(stepId==null?null:steps.findById(UUID.fromString(stepId.toString())).orElse(null))
                        .humanActionAt(acted==null?null:LocalDateTime.parse(acted.toString())).build()); migratedRecords++;
            }
            migratedInstances++;
        }
        return Map.of("migratedInstances",migratedInstances,"migratedRecords",migratedRecords);
    }
}
