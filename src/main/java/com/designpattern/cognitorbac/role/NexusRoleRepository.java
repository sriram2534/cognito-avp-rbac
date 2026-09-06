package com.designpattern.cognitorbac.role;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NexusRoleRepository extends MongoRepository<NexusRole, String> {
    Optional<NexusRole> findByRoleId(String roleId);
    Optional<NexusRole> findByRoleKey(String roleKey);
    List<NexusRole> findByRoleIdIn(Collection<String> roleIds);
    List<NexusRole> findByStatusOrderByModuleAscNameAsc(NexusRoleStatus status);
}
