package com.designpattern.cognitorbac.dto.avp;

import java.time.Instant;
import java.util.List;

/**
 * Response representing the current state of the Cedar schema in AVP.
 */
public record SchemaResponse(
        String policyStoreId,
        String namespace,
        List<String> actions,
        List<String> entityTypes,
        Instant lastUpdatedDate
) {
}
