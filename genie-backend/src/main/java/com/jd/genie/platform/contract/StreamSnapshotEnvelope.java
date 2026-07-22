package com.jd.genie.platform.contract;

import com.jd.genie.model.response.GptProcessResult;
import java.util.List;

public record StreamSnapshotEnvelope(
    int payloadVersion,
    boolean truncated,
    List<GptProcessResult> events
) {
}
