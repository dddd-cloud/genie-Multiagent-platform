import type { GptProcessResultEvent } from './agent';

export type JsonPrimitive = string | number | boolean | null;

export type JsonValue =
  | JsonPrimitive
  | JsonObject
  | JsonValue[];

export interface JsonObject {
  [key: string]: JsonValue;
}

export interface StreamSnapshotEnvelope {
  payloadVersion: 1;
  truncated: boolean;
  events: GptProcessResultEvent[];
}
