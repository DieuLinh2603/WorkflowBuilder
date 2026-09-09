package com.company.workflowbuilder.dto.request;

import lombok.Data;
import java.util.*;

@Data
public class TaskActionRequest {
    private String comment;
    private Map<String, Object> fields = new HashMap<>();
}
