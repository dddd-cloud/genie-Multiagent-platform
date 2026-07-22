package com.jd.genie.platform.contract;

import java.util.List;

public record PageResponse<T>(
    List<T> items,
    int page,
    int pageSize,
    boolean hasMore
) {
}
