import type { Phase2ModelResponse } from '@/contracts/phase2';
import { phase2DeleteWithParams, phase2Get, phase2Post, phase2Put } from './client';

const MODELS_BASE = '/api/v2/models';

export type LlmModelWriteBody = {
  name: string;
  displayName: string;
  model: string;
  baseUrl?: string;
  interfaceUrl?: string;
  maxTokens?: number;
  temperature?: number;
  maxInputTokens?: number;
  apiKey?: string;
};

export function listModels(signal?: AbortSignal) {
  return phase2Get<Phase2ModelResponse[]>(MODELS_BASE, undefined, signal);
}

export function getModel(id: string, signal?: AbortSignal) {
  return phase2Get<Phase2ModelResponse>(
    `${MODELS_BASE}/${encodeURIComponent(id)}`,
    undefined,
    signal,
  );
}

export function createModel(body: LlmModelWriteBody, signal?: AbortSignal) {
  return phase2Post<Phase2ModelResponse>(MODELS_BASE, body, signal);
}

export function updateModel(
  id: string,
  body: LlmModelWriteBody,
  signal?: AbortSignal,
) {
  return phase2Put<Phase2ModelResponse>(
    `${MODELS_BASE}/${encodeURIComponent(id)}`,
    body,
    signal,
  );
}

export function deleteModel(id: string, signal?: AbortSignal) {
  return phase2DeleteWithParams<void>(
    `${MODELS_BASE}/${encodeURIComponent(id)}`,
    undefined,
    signal,
  );
}
