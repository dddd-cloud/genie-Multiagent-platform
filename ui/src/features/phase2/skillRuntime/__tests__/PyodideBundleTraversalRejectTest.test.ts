import { describe, expect, it } from 'vitest';
import { BundleValidationError, unpackSkillBundle } from '../bundleGuard';
import { buildStoreZip } from './zipFixture';

describe('PyodideBundleTraversalRejectTest', () => {
  it('throws when zip contains a traversal entry like ../evil.py', () => {
    const bytes = buildStoreZip({'../evil.py': 'print("evil")\n',});

    expect(() => unpackSkillBundle(bytes)).toThrow(BundleValidationError);
    try {
      unpackSkillBundle(bytes);
    } catch (error) {
      expect(error).toBeInstanceOf(BundleValidationError);
      expect((error as BundleValidationError).errorCode).toBe(
        'SKILL_PACKAGE_INVALID',
      );
      expect((error as BundleValidationError).message).toMatch(
        /unsafe zip entry path/,
      );
    }
  });
});
