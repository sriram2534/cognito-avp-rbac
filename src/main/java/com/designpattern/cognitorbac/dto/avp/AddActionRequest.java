package com.designpattern.cognitorbac.dto.avp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Request to add a new action to the Cedar schema.
 *
 * @param actionName  the action name (PascalCase, e.g. "Download", "Archive")
 * @param memberOf    list of parent actions this action inherits from (e.g. ["Read"])
 * @param description optional description
 */
public record AddActionRequest(

        @NotBlank(message = "actionName is required")
        @Pattern(regexp = "^[A-Z][a-zA-Z]+$",
                message = "actionName must be PascalCase (e.g. Download, Archive)")
        String actionName,

        List<String> memberOf,

        String description
) {
    public List<String> memberOf() {
        return memberOf == null ? List.of() : memberOf;
    }
}
