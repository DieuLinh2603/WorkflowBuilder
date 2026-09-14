package com.company.workflowbuilder.mapper;

import com.company.workflowbuilder.dto.response.UserResponse;
import com.company.workflowbuilder.dto.response.UserDropdownResponse;
import com.company.workflowbuilder.dto.response.UserListItemResponse;
import com.company.workflowbuilder.entity.user.User;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) return null;
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .jobTitle(user.getJobTitle())
                .managerId(user.getManager() == null ? null : user.getManager().getId())
                .managerName(user.getManager() == null ? null : user.getManager().getDisplayName())
                .dataSource(user.getDataSource())
                .active(user.isActive())
                .systemRoles(user.getSystemRoles() == null ? null : new LinkedHashSet<>(user.getSystemRoles()))
                .moduleCodes(user.getModuleCodes() == null ? null : new LinkedHashSet<>(user.getModuleCodes()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public List<UserResponse> toResponseList(List<User> users) {
        return users == null ? null : users.stream().map(this::toResponse).toList();
    }

    public UserListItemResponse toListItemResponse(User user) {
        if (user == null) return null;
        return UserListItemResponse.builder()
                .id(user.getId())
                .avatarInitials(getInitials(user.getDisplayName()))
                .avatarColor(getColor(user.getDisplayName()))
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .jobTitle(user.getJobTitle())
                .managerId(user.getManager() == null ? null : user.getManager().getId())
                .managerName(user.getManager() == null ? null : user.getManager().getDisplayName())
                .systemRoles(user.getSystemRoles() == null ? null : new LinkedHashSet<>(user.getSystemRoles()))
                .moduleCodes(user.getModuleCodes() == null ? null : new LinkedHashSet<>(user.getModuleCodes()))
                .createdAt(user.getCreatedAt())
                .build();
    }

    public UserDropdownResponse toDropdownResponse(User user) {
        if (user == null) return null;
        return UserDropdownResponse.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .jobTitle(user.getJobTitle())
                .avatarInitials(getInitials(user.getDisplayName()))
                .avatarColor(getColor(user.getDisplayName()))
                .build();
    }

    String getInitials(String name) {
        if (name == null || name.trim().isEmpty())
            return "NA";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    String getColor(String name) {
        if (name == null || name.trim().isEmpty())
            return "bg-gray-500";
        String[] colors = {
                "bg-red-500", "bg-orange-500", "bg-amber-500", "bg-green-500", "bg-emerald-500",
                "bg-teal-500", "bg-cyan-500", "bg-blue-500", "bg-indigo-500", "bg-violet-500",
                "bg-purple-500", "bg-fuchsia-500", "bg-pink-500", "bg-rose-500"
        };
        int hash = Math.abs(name.hashCode());
        return colors[hash % colors.length];
    }
}
