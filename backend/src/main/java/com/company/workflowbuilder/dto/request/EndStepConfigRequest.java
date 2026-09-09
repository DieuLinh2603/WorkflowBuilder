package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class EndStepConfigRequest {
    public enum Outcome { COMPLETED, APPROVED, REJECTED, CANCELLED }

    @NotNull private Outcome outcome = Outcome.COMPLETED;
    @NotBlank @Size(max = 160) private String title = "Yêu cầu đã hoàn tất";
    @Size(max = 1000) private String message = "Yêu cầu {{requestCode}} đã kết thúc.";
    private boolean notifyRequester = true;
    private boolean notifyRecordRecipient = false;
}
