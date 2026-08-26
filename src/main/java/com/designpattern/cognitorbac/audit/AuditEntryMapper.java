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
    @Mapping(target = "groupName", source = "groupName")
    @Mapping(target = "targetUsername", source = "targetUsername")
    @Mapping(target = "actorSub", expression = "java(context == null ? null : context.getActorSub())")
    @Mapping(target = "actorEmail", expression = "java(context == null ? null : context.getActorEmail())")
    @Mapping(target = "actorGroups", expression = "java(context == null ? java.util.List.of() : context.getActorGroups())")
    @Mapping(target = "reason", expression = "java(context == null ? null : context.getReason())")
    @Mapping(target = "correlationId", expression = "java(context == null ? null : context.getCorrelationId())")
    @Mapping(target = "changes", source = "changes")
    AuditEntry toLegacyEntry(AuditAction action, String groupName, String targetUsername,
                             AuditContext context, List<FieldChange> changes);

    @Mapping(target = "action", source = "action")
    @Mapping(target = "aggregateType", source = "aggregateType")
    @Mapping(target = "aggregateId", source = "aggregateId")
    @Mapping(target = "groupName", source = "roleKey")
    @Mapping(target = "roleKey", source = "roleKey")
    @Mapping(target = "permissionId", source = "permissionId")
    @Mapping(target = "actorSub", expression = "java(context == null ? null : context.getActorSub())")
    @Mapping(target = "actorEmail", expression = "java(context == null ? null : context.getActorEmail())")
    @Mapping(target = "actorGroups", expression = "java(context == null ? java.util.List.of() : context.getActorGroups())")
    @Mapping(target = "reason", expression = "java(context == null ? null : context.getReason())")
    @Mapping(target = "correlationId", expression = "java(context == null ? null : context.getCorrelationId())")
    @Mapping(target = "changes", source = "changes")
    AuditEntry toAuthorizationEntry(AuditAction action, String aggregateType, String aggregateId,
                                    String roleKey, String permissionId,
                                    AuditContext context, List<FieldChange> changes);
}
