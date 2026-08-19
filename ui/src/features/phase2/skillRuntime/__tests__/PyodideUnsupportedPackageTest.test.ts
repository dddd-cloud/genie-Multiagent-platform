import { describe, expect, it } from 'vitest';
import { isAllowedPyodidePackageSpec } from '../signal';

describe('PyodideUnsupportedPackageTest', () => {
  it('rejects https:// and git+ package specs', () => {
    expect(isAllowedPyodidePackageSpec('https://evil.example/pkg.whl')).toBe(
      false,
    );
    expect(isAllowedPyodidePackageSpec('git+https://github.com/x/y.git')).toBe(
      false,
    );
    expect(isAllowedPyodidePackageSpec('http://cdn.example/x')).toBe(false);
    expect(isAllowedPyodidePackageSpec('file:/tmp/x')).toBe(false);
  });

  it('allows version-constrained specs for allowlisted packages', () => {
    expect(isAllowedPyodidePackageSpec('pandas==2.2.0')).toBe(true);
    expect(isAllowedPyodidePackageSpec('numpy>=1.26')).toBe(true);
  });
});
