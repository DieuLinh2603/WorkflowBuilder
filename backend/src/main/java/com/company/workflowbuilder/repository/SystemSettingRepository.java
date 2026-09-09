package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
