package com.company.workflowbuilder.config;

import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

// @Configuration
public class DatabaseSeeder {

    @Bean
    public CommandLineRunner seedDatabase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByEmail("admin@company.com").isEmpty()) {
                User admin = new User();
                admin.setEmail("admin@company.com");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setDisplayName("System Admin");
                admin.setJobTitle("Administrator");
                admin.setActive(true);
                admin.setSystemRoles(Set.of(SystemRole.ADMIN));
                
                userRepository.save(admin);
                System.out.println("Seeded default admin user: admin@company.com / admin123");
            }
        };
    }
}
