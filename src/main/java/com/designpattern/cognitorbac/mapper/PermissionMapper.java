package com.designpattern.cognitorbac.mapper;

import com.designpattern.cognitorbac.dto.PermissionResponse;
import com.designpattern.cognitorbac.permission.Permission;
import com.designpattern.cognitorbac.permission.PermissionStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

/**
 * Centralizes permission representations. The factory owns construction-only
 * concerns (UUID, lifecycle defaults, and actor fields); MapStruct generates
 * the external response mapping.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionMapper {

    @Mapping(target = "displayKey", expression = "java(permission.getModule() + \":\" + permission.getResourceType() + \":\" + permission.getAccess())")
    PermissionResponse toResponse(Permission permission);

    Permission toEntity(String module, String resourceType, String access, String description, String actorSub);

    @ObjectFactory
    default Permission createPermission(String module, String resourceType, String access,
                                        String description, String actorSub) {
        return new Permission(module, resourceType, access, description, PermissionStatus.ACTIVE, actorSub);
    }
}
