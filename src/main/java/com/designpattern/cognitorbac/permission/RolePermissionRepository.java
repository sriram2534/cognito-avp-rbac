package com.designpattern.cognitorbac.permission;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository extends MongoRepository<RolePermission, String> {
    Optional<RolePermission> findByRoleIdAndPermissionId(String roleId, String permissionId);
    List<RolePermission> findByRoleIdAndStatus(String roleId, RolePermissionStatus status);
    List<RolePermission> findByPermissionIdAndStatus(String permissionId, RolePermissionStatus status);
    List<RolePermission> findByPermissionId(String permissionId);
    long countByRoleIdAndStatus(String roleId, RolePermissionStatus status);
}
