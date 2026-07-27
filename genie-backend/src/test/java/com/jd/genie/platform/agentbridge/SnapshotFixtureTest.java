package com.jd.genie.platform.agentbridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotFixtureTest {

    private static final Path SNAPSHOTS = Path.of("..", "docs", "mvp-contract", "fixtures", "snapshot");
    private static final Path SCHEMA = Path.of("..", "docs", "mvp-contract", "schema", "stream-snapshot-v1.schema.json");
    private static final List<String> VALID_FIXTURES = List.of(
            "react-success.json",
            "plan-success.json",
            "failed.json",
            "interrupted.json",
            "truncated.json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void frozenFixturesMatchSnapshotV1SchemaAndRoundTrip() throws Exception {
        JsonNode schema = objectMapper.readTree(SCHEMA.toFile());

        for (String fixture : VALID_FIXTURES) {
            JsonNode source = objectMapper.readTree(SNAPSHOTS.resolve(fixture).toFile());
            assertFrozenEnvelopeSchema(schema, source);

            StreamSnapshotEnvelope envelope = objectMapper.treeToValue(source, StreamSnapshotEnvelope.class);
            JsonNode roundTrip = objectMapper.valueToTree(envelope);
            assertFrozenEnvelopeSchema(schema, roundTrip);
            assertEquals(source.path("events").size(), roundTrip.path("events").size());
        }
    }

    @Test
    void invalidVersionFixtureFailsSchemaConst() throws Exception {
        JsonNode schema = objectMapper.readTree(SCHEMA.toFile());
        JsonNode invalid = objectMapper.readTree(SNAPSHOTS.resolve("invalid-version.json").toFile());

        AssertionError error = assertThrows(AssertionError.class, () -> assertFrozenEnvelopeSchema(schema, invalid));
        assertTrue(error.getMessage().contains("payloadVersion"));
    }

    @Test
    void malformedFixtureCannotBeParsed() throws Exception {
        String malformed = Files.readString(SNAPSHOTS.resolve("malformed-json.txt"));
        assertThrows(Exception.class, () -> objectMapper.readTree(malformed));
    }

    private void assertFrozenEnvelopeSchema(JsonNode schema, JsonNode value) {
        assertTrue(value.isObject(), "Snapshot must be an object");
        Set<String> allowed = Set.of("payloadVersion", "truncated", "events");
        value.fieldNames().forEachRemaining(field -> assertTrue(allowed.contains(field), "Unexpected field: " + field));

        schema.path("required").forEach(required ->
                assertTrue(value.has(required.asText()), "Missing field: " + required.asText()));
        assertEquals(schema.path("properties").path("payloadVersion").path("const").asInt(),
                value.path("payloadVersion").asInt(), "payloadVersion must match schema const");
        assertTrue(value.path("truncated").isBoolean(), "truncated must be boolean");
        assertTrue(value.path("events").isArray(), "events must be an array");
        value.path("events").forEach(event -> assertTrue(event.isObject(), "events items must be objects"));
        assertFalse(schema.path("additionalProperties").asBoolean(), "Frozen schema must reject extra properties");
    }
}
