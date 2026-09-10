package com.company.workflowbuilder.dto.request;

import lombok.Data;
import java.util.*;

@Data
public class TaskActionRequest {
    private String comment;
    // null uses the step defaults; an empty list explicitly removes optional formulas.
    private List<com.company.workflowbuilder.dto.CalculatedOutput> calculatedOutputs;
    private List<com.company.workflowbuilder.dto.ReviewResultItem> reviewResults = new ArrayList<>();
    private Map<String, Object> fields = new HashMap<>();
    private Set<Integer> selectedRowNumbers;
    private String selectedRowsOutcome;
}
