package com.designpattern.cognitorbac.audit;

import com.designpattern.cognitorbac.dto.CreateGroupRequest;
import com.designpattern.cognitorbac.dto.UpdateGroupRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AOP aspect that intercepts write operations on {@link com.designpattern.cognitorbac.service.GroupService}
 * and records an {@link AuditEntry} for each successful execution.
 *
 * <p>Audit is recorded AFTER the method returns successfully ({@code @AfterReturning}),
 * so failed operations are never audited as if they succeeded.
 * The caller identity and reason are pulled from {@link AuditContext} which is
 * populated per-request by {@link AuditContextFilter}.</p>
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;
    private final FieldChangeMapper fieldChangeMapper;

    public AuditAspect(AuditService auditService, FieldChangeMapper fieldChangeMapper) {
        this.auditService = auditService;
        this.fieldChangeMapper = fieldChangeMapper;
    }

    @AfterReturning("execution(* com.designpattern.cognitorbac.service.GroupService.createGroup(..))")
    public void onGroupCreated(JoinPoint jp) {
        try {
            CreateGroupRequest request = (CreateGroupRequest) jp.getArgs()[0];
            auditService.record(AuditAction.GROUP_CREATED, request.groupName());
        } catch (Exception ex) {
            log.error("Audit aspect failed on GROUP_CREATED — business operation unaffected", ex);
        }
    }

    @AfterReturning("execution(* com.designpattern.cognitorbac.service.GroupService.updateGroup(..))")
    public void onGroupUpdated(JoinPoint jp) {
        try {
            String groupName = (String) jp.getArgs()[0];
            UpdateGroupRequest request = (UpdateGroupRequest) jp.getArgs()[1];
            auditService.record(AuditAction.GROUP_UPDATED, groupName, null, buildUpdateChanges(request));
        } catch (Exception ex) {
            log.error("Audit aspect failed on GROUP_UPDATED — business operation unaffected", ex);
        }
    }

    @AfterReturning("execution(* com.designpattern.cognitorbac.service.GroupService.deleteGroup(..))")
    public void onGroupDeleted(JoinPoint jp) {
        try {
            auditService.record(AuditAction.GROUP_DELETED, (String) jp.getArgs()[0]);
        } catch (Exception ex) {
            log.error("Audit aspect failed on GROUP_DELETED — business operation unaffected", ex);
        }
    }

    @AfterReturning("execution(* com.designpattern.cognitorbac.service.GroupService.addUserToGroup(..))")
    public void onUserAdded(JoinPoint jp) {
        try {
            String groupName = (String) jp.getArgs()[0];
            String username = (String) jp.getArgs()[1];
            auditService.record(AuditAction.USER_ADDED_TO_GROUP, groupName, username);
        } catch (Exception ex) {
            log.error("Audit aspect failed on USER_ADDED_TO_GROUP — business operation unaffected", ex);
        }
    }

    @AfterReturning("execution(* com.designpattern.cognitorbac.service.GroupService.removeUserFromGroup(..))")
    public void onUserRemoved(JoinPoint jp) {
        try {
            String groupName = (String) jp.getArgs()[0];
            String username = (String) jp.getArgs()[1];
            auditService.record(AuditAction.USER_REMOVED_FROM_GROUP, groupName, username);
        } catch (Exception ex) {
            log.error("Audit aspect failed on USER_REMOVED_FROM_GROUP — business operation unaffected", ex);
        }
    }

    private List<FieldChange> buildUpdateChanges(UpdateGroupRequest request) {
        List<FieldChange> changes = new ArrayList<>();
        if (request.description() != null)
            changes.add(fieldChangeMapper.toFieldChange("description", null, request.description()));
        if (request.precedence() != null)
            changes.add(fieldChangeMapper.toFieldChange("precedence", null, request.precedence()));
        if (request.roleArn() != null)
            changes.add(fieldChangeMapper.toFieldChange("roleArn", null, request.roleArn()));
        return changes;
    }
}
