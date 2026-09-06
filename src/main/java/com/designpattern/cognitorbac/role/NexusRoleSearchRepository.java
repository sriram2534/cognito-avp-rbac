package com.designpattern.cognitorbac.role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NexusRoleSearchRepository {
    Page<NexusRole> search(RoleFilter filter, Pageable pageable);
}
