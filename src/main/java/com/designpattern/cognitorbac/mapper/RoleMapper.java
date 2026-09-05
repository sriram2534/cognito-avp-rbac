package com.designpattern.cognitorbac.mapper;

import com.designpattern.cognitorbac.dto.RoleResponse;
import com.designpattern.cognitorbac.dto.UserRoleResponse;
import com.designpattern.cognitorbac.role.NexusRole;
import com.designpattern.cognitorbac.role.NexusUserRole;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoleMapper {

    RoleResponse toResponse(NexusRole role);

    UserRoleResponse toUserRoleResponse(NexusUserRole userRole);

    NexusRole toRoleEntity(String roleKey, String module, String name, String displayName,
                            String description, String actorSub);

    NexusUserRole toUserRoleEntity(String userSub, String roleId, String actorSub);

    @ObjectFactory
    default NexusRole createRole(String roleKey, String module, String name, String displayName,
                                  String description, String actorSub) {
        return new NexusRole(roleKey, module, name, displayName, description, actorSub);
    }

    @ObjectFactory
    default NexusUserRole createUserRole(String userSub, String roleId, String actorSub) {
        return new NexusUserRole(userSub, roleId, actorSub);
    }
}
