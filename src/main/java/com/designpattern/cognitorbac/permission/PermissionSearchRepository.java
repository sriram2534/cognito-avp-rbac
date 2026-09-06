package com.designpattern.cognitorbac.permission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PermissionSearchRepository {
    Page<Permission> search(PermissionFilter filter, Pageable pageable);
}
