package com.designpattern.cognitorbac.controller;

import com.designpattern.cognitorbac.dto.PagedResponse;
import com.designpattern.cognitorbac.dto.UserResponse;
import com.designpattern.cognitorbac.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for reading Cognito users and their group memberships.
 *
 * <p>All endpoints require a valid Cognito JWT (any authenticated user).</p>
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Lists users from the configured Cognito user pool.
     *
     * @param limit         optional page size (max 60)
     * @param nextToken     optional opaque cursor from a previous page
     * @param includeRoles when true, resolves each user's Nexus roles (extra cost)
     */
    @GetMapping
    public PagedResponse<UserResponse> listUsers(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String nextToken,
            @RequestParam(defaultValue = "false") boolean includeRoles) {
        return userService.listUsers(limit, nextToken, includeRoles);
    }

    /**
     * Fetches a single user with full detail and group memberships.
     */
    @GetMapping("/{username}")
    public ResponseEntity<UserResponse> getUser(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUser(username));
    }
}
