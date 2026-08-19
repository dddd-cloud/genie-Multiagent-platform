type ResetFn = () => void;

const resets = new Set<ResetFn>();

/**
 * Seam for clearing in-memory, user-scoped caches at logout. Without it, state cached in a module
 * (preferences, workspace listings, marketplace results) would survive into the next login inside the
 * same tab and leak one account's data into another's session.
 *
 * Returns an unregister function so React effects can clean up.
 */
export function registerUserScopedReset(reset: ResetFn): () => void {
  resets.add(reset);
  return () => {
    resets.delete(reset);
  };
}

export function resetUserScopedState(): void {
  resets.forEach((reset) => {
    try {
      reset();
    } catch {
      // A failing cache reset must not stop logout from completing.
    }
  });
}
