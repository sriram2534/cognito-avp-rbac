package com.designpattern.cognitorbac.role;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NexusUserRoleRepository extends MongoRepository<NexusUserRole, String> {
    Optional<NexusUserRole> findByUserSubAndRoleId(String userSub, String roleId);
    List<NexusUserRole> findByUserSubAndStatus(String userSub, NexusUserRoleStatus status);
    List<NexusUserRole> findByRoleIdAndStatus(String roleId, NexusUserRoleStatus status);
}
