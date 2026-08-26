package com.designpattern.cognitorbac.permission;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PermissionRepository extends MongoRepository<Permission, String> {
    Optional<Permission> findByModuleAndResourceTypeAndAccess(String module, String resourceType, String access);
    Optional<Permission> findByPermissionId(String permissionId);
    boolean existsByPermissionId(String permissionId);
}
