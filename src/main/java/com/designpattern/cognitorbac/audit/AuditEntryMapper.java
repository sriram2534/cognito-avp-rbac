package com.designpattern.cognitorbac.audit;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Converts audit facts into append-only persistence records. The mapper owns
 * the representation only; AuditService remains responsible for persistence
 * and transaction boundaries.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuditEntryMapper {

    @Mapping(target = "action", source = "action")
    @Mapping(target = "aggregateType", source = "aggregateType")
    @Mapping(target = "aggregateId", source = "aggregateId")
    @Mapping(target = "roleName", source = "roleName")
    @Mapping(target = "roleKey", source = "roleKey")
    @Mapping(target = "permissionId", source = "permissionId")
    @Mapping(target = "userSub", expression = "java(context.getUserSub())")
    @Mapping(target = "userEmail", expression = "java(context.getUserEmail())")
    @Mapping(target = "reason", expression = "java(context.getReason())")
    @Mapping(target = "correlationId", expression = "java(context.getCorrelationId())")
    @Mapping(target = "changes", source = "changes")
    AuditEntry toAuthorizationEntry(AuditAction action, String aggregateType, String aggregateId,
                                    String roleName, String roleKey, String permissionId,
                                    AuditContext context, List<FieldChange> changes);
}
