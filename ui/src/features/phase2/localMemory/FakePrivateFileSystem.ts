import { assertAllowedMemoryPath } from './paths';
import type { PrivateFileSystem } from './PrivateFileSystem';

export type FakePrivateFileSystemOptions = {
  available?: boolean;
  /** When set, writeText throws with this message. */
  writeError?: string | null;
  /** When true, read after write returns previous/stale content. */
  readBackMismatch?: boolean;
};

export class FakePrivateFileSystem implements PrivateFileSystem {
  private readonly files = new Map<string, string>();
  available: boolean;
  writeError: string | null;
  readBackMismatch: boolean;

  constructor(options: FakePrivateFileSystemOptions = {}) {
    this.available = options.available ?? true;
    this.writeError = options.writeError ?? null;
    this.readBackMismatch = options.readBackMismatch ?? false;
  }

  async isAvailable(): Promise<boolean> {
    return this.available;
  }

  async readText(path: string): Promise<string | null> {
    assertAllowedMemoryPath(path);
    if (!this.available) {
      throw new Error('OPFS unavailable');
    }
    return this.files.has(path) ? (this.files.get(path) as string) : null;
  }

  async writeText(path: string, content: string): Promise<void> {
    assertAllowedMemoryPath(path);
    if (!this.available) {
      throw new Error('OPFS unavailable');
    }
    if (this.writeError) {
      throw new Error(this.writeError);
    }
    if (this.readBackMismatch) {
      // Keep previous content (or empty) so read-back fails exact compare.
      if (!this.files.has(path)) {
        this.files.set(path, '');
      }
      return;
    }
    this.files.set(path, content);
  }

  async remove(path: string): Promise<void> {
    assertAllowedMemoryPath(path);
    this.files.delete(path);
  }

  seed(path: string, content: string): void {
    assertAllowedMemoryPath(path);
    this.files.set(path, content);
  }

  dump(): Map<string, string> {
    return new Map(this.files);
  }
}
