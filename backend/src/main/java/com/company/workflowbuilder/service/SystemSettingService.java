package com.company.workflowbuilder.service;
import com.company.workflowbuilder.entity.SystemSetting;
import com.company.workflowbuilder.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class SystemSettingService{
 public static final String DEADLINE_LEAD_HOURS="deadlineReminderLeadTimeHours";
 private final SystemSettingRepository repository; private final CurrentUserService currentUser;
 @Transactional(readOnly=true) public int deadlineLeadHours(){return repository.findById(DEADLINE_LEAD_HOURS).map(x->Integer.parseInt(x.getValue())).orElse(24);}
 @Transactional(readOnly=true) public String value(String key){return repository.findById(key).map(SystemSetting::getValue).orElse(null);}
 @Transactional public int updateDeadlineLeadHours(int hours){currentUser.requireAdmin();if(hours<1||hours>720)throw new IllegalArgumentException("Reminder lead time must be between 1 and 720 hours");repository.save(new SystemSetting(DEADLINE_LEAD_HOURS,String.valueOf(hours)));return hours;}
 @Transactional public void updateEndpoint(String key,String value){currentUser.requireAdmin();if(!java.util.Set.of("reminder.teams.webhookUrl","reminder.webhook.url").contains(key))throw new IllegalArgumentException("Unsupported setting key");repository.save(new SystemSetting(key,value==null?"":value.trim()));}
}
