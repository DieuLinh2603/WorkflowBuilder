package com.company.workflowbuilder.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting {
    @Id
    @Column(name = "setting_key")
    private String key;
    @Column(name = "setting_value", nullable = false)
    private String value;
}
