package com.company.workflowbuilder.config;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.*;
import javax.sql.DataSource;
@Configuration
public class SchedulerConfig{
 @Bean LockProvider lockProvider(DataSource dataSource){return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder().withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource)).usingDbTime().build());}
}
