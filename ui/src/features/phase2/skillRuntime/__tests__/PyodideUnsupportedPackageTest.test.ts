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
});
