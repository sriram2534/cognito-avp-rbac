package com.designpattern.cognitorbac.audit;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Maps explicit before/after values into embedded audit deltas. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FieldChangeMapper {

    FieldChange toFieldChange(String field, Object oldValue, Object newValue);
}
