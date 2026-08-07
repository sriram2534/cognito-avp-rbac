package com.designpattern.cognitorbac.dto;

import java.util.List;

/**
 * Generic cursor-paginated envelope. Cognito uses opaque pagination tokens
 * rather than offset/limit, so {@code nextToken} is passed back to the caller
 * to retrieve the following page.
 *
 * @param items     the page contents
 * @param nextToken opaque token for the next page; {@code null} when exhausted
 */
public record PagedResponse<T>(
        List<T> items,
        String nextToken
) {
    public static <T> PagedResponse<T> of(List<T> items, String nextToken) {
        return new PagedResponse<>(items, nextToken);
    }
}
