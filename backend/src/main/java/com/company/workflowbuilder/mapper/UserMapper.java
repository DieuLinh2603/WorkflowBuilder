package com.company.workflowbuilder.mapper;

import com.company.workflowbuilder.dto.response.UserResponse;
import com.company.workflowbuilder.entity.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "managerId", source = "manager.id")
    @Mapping(target = "managerName", source = "manager.displayName")
    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);

    @Mapping(target = "managerName", source = "manager.displayName")
    @Mapping(target = "avatarInitials", expression = "java(getInitials(user.getDisplayName()))")
    @Mapping(target = "avatarColor", expression = "java(getColor(user.getDisplayName()))")
    com.company.workflowbuilder.dto.response.UserListItemResponse toListItemResponse(User user);

    @Mapping(target = "avatarInitials", expression = "java(getInitials(user.getDisplayName()))")
    @Mapping(target = "avatarColor", expression = "java(getColor(user.getDisplayName()))")
    com.company.workflowbuilder.dto.response.UserDropdownResponse toDropdownResponse(User user);

    @Named("getInitials")
    default String getInitials(String name) {
        if (name == null || name.trim().isEmpty())
            return "NA";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    @Named("getColor")
    default String getColor(String name) {
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
