package com.designpattern.cognitorbac.permission;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends MongoRepository<Permission, String>, PermissionSearchRepository {
    Optional<Permission> findByModuleAndResourceTypeAndAccess(String module, String resourceType, String access);
    Optional<Permission> findByPermissionId(String permissionId);
    List<Permission> findByPermissionIdIn(Collection<String> permissionIds);
    boolean existsByPermissionId(String permissionId);
}
