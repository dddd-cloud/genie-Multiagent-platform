import { createContext, useContext } from 'react';
import type { UserResponse } from '@/contracts';

export type AuthStatus = 'booting' | 'authenticated' | 'unauthenticated';

export type AuthContextValue = {
  status: AuthStatus;
  user: UserResponse | null;
  bootError: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  retryBoot: () => void;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
