import { describe, expect, it } from 'vitest';
import { isAllowedPyodidePackageSpec } from '../signal';

describe('PyodidePurePythonPackageTest', () => {
  it('allows pure PyPI-style specs like numpy==1.26', () => {
    expect(isAllowedPyodidePackageSpec('numpy==1.26')).toBe(true);
    expect(isAllowedPyodidePackageSpec('pandas')).toBe(true);
    expect(isAllowedPyodidePackageSpec('scikit-learn>=1.0')).toBe(true);
  });
});
