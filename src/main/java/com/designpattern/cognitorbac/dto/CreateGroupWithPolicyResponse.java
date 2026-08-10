package com.designpattern.cognitorbac.dto;

import com.designpattern.cognitorbac.dto.avp.PolicyResponse;

/**
 * Combined response for the create-group-with-policy operation.
 * Groups and their AVP policies are always created together to prevent
 * the window where a group exists but has no policy.
 */
public record CreateGroupWithPolicyResponse(
        GroupResponse group,
        PolicyResponse policy
) {
}
