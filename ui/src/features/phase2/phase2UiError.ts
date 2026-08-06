import { MvpApiError } from '@/services/apiError';
import { getPhase2ErrorMessage } from '@/services/phase2/errorMessages';

export function isVersionConflict(err: unknown): boolean {
  return err instanceof MvpApiError && err.code === 'VERSION_CONFLICT';
}

export function isSkillInUse(err: unknown): boolean {
  return err instanceof MvpApiError && err.code === 'SKILL_IN_USE';
}

export function phase2ErrorMessage(err: unknown, fallback?: string): string {
  if (err instanceof MvpApiError) {
    return getPhase2ErrorMessage(err.code, err.message || fallback);
  }
  if (err instanceof Error && err.message.trim()) {
    return err.message;
  }
  return getPhase2ErrorMessage('', fallback);
}
