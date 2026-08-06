export interface PrivateFileSystem {
  readText(path: string): Promise<string | null>;
  writeText(path: string, content: string): Promise<void>;
  remove(path: string): Promise<void>;
  isAvailable(): Promise<boolean>;
}
