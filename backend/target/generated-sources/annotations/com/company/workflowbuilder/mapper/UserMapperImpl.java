package com.company.workflowbuilder.mapper;

import com.company.workflowbuilder.dto.response.UserDropdownResponse;
import com.company.workflowbuilder.dto.response.UserListItemResponse;
import com.company.workflowbuilder.dto.response.UserResponse;
import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.user.User;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-10T21:09:49+0700",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.100.v20260826-1225, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserResponse toResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UserResponse.UserResponseBuilder userResponse = UserResponse.builder();

        userResponse.managerId( userManagerId( user ) );
        userResponse.managerName( userManagerDisplayName( user ) );
        userResponse.active( user.isActive() );
        userResponse.createdAt( user.getCreatedAt() );
        userResponse.dataSource( user.getDataSource() );
        userResponse.displayName( user.getDisplayName() );
        userResponse.email( user.getEmail() );
        userResponse.id( user.getId() );
        userResponse.jobTitle( user.getJobTitle() );
        Set<String> set = user.getModuleCodes();
        if ( set != null ) {
            userResponse.moduleCodes( new LinkedHashSet<String>( set ) );
        }
        Set<SystemRole> set1 = user.getSystemRoles();
        if ( set1 != null ) {
            userResponse.systemRoles( new LinkedHashSet<SystemRole>( set1 ) );
        }
        userResponse.updatedAt( user.getUpdatedAt() );

        return userResponse.build();
    }

    @Override
    public List<UserResponse> toResponseList(List<User> users) {
        if ( users == null ) {
            return null;
        }

        List<UserResponse> list = new ArrayList<UserResponse>( users.size() );
        for ( User user : users ) {
            list.add( toResponse( user ) );
        }

        return list;
    }

    @Override
    public UserListItemResponse toListItemResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UserListItemResponse.UserListItemResponseBuilder userListItemResponse = UserListItemResponse.builder();

        userListItemResponse.managerId( userManagerId( user ) );
        userListItemResponse.managerName( userManagerDisplayName( user ) );
        userListItemResponse.createdAt( user.getCreatedAt() );
        userListItemResponse.displayName( user.getDisplayName() );
        userListItemResponse.email( user.getEmail() );
        userListItemResponse.id( user.getId() );
        userListItemResponse.jobTitle( user.getJobTitle() );
        Set<String> set = user.getModuleCodes();
        if ( set != null ) {
            userListItemResponse.moduleCodes( new LinkedHashSet<String>( set ) );
        }
        Set<SystemRole> set1 = user.getSystemRoles();
        if ( set1 != null ) {
            userListItemResponse.systemRoles( new LinkedHashSet<SystemRole>( set1 ) );
        }

        userListItemResponse.avatarInitials( getInitials(user.getDisplayName()) );
        userListItemResponse.avatarColor( getColor(user.getDisplayName()) );

        return userListItemResponse.build();
    }

    @Override
    public UserDropdownResponse toDropdownResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UserDropdownResponse.UserDropdownResponseBuilder userDropdownResponse = UserDropdownResponse.builder();

        userDropdownResponse.displayName( user.getDisplayName() );
        userDropdownResponse.id( user.getId() );
        userDropdownResponse.jobTitle( user.getJobTitle() );

        userDropdownResponse.avatarInitials( getInitials(user.getDisplayName()) );
        userDropdownResponse.avatarColor( getColor(user.getDisplayName()) );

        return userDropdownResponse.build();
    }

    private UUID userManagerId(User user) {
        if ( user == null ) {
            return null;
        }
        User manager = user.getManager();
        if ( manager == null ) {
            return null;
        }
        UUID id = manager.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }

    private String userManagerDisplayName(User user) {
        if ( user == null ) {
            return null;
        }
        User manager = user.getManager();
        if ( manager == null ) {
            return null;
        }
        String displayName = manager.getDisplayName();
        if ( displayName == null ) {
            return null;
        }
        return displayName;
    }
}
