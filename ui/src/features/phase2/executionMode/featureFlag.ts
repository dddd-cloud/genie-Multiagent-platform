export function isPhase2Enabled(): boolean {
  return import.meta.env.VITE_PHASE2_ENABLED === 'true';
}
