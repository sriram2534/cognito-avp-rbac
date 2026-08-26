package com.designpattern.cognitorbac.mapper;

import com.designpattern.cognitorbac.dto.RolePermissionResponse;
import com.designpattern.cognitorbac.permission.RolePermission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

/** Maps role-permission persistence records without leaking construction logic to services. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RolePermissionMapper {

    RolePermissionResponse toResponse(RolePermission rolePermission);

    RolePermission toEntity(String roleKey, String permissionId, String actorSub);

    @ObjectFactory
    default RolePermission createRolePermission(String roleKey, String permissionId, String actorSub) {
        return new RolePermission(roleKey, permissionId, actorSub);
    }
}
