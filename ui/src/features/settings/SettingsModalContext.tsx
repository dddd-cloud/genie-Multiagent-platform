import {
  createContext,
  memo,
  useCallback,
  useContext,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  DEFAULT_SETTINGS_PATH,
  canonicalizeSettingsPath,
  hashToSettingsPath,
  isTransientSettingsPath,
  settingsPathToHash,
  splitPathSearch,
} from './settingsHash';

export { DEFAULT_SETTINGS_PATH };

type SettingsModalContextValue = {
  open: boolean;
  settingsPath: string;
  settingsState: unknown;
  session: number;
  openSettings: (path?: string, state?: unknown) => void;
  closeSettings: () => void;
  setSettingsState: (state: unknown) => void;
};

const SettingsModalContext = createContext<SettingsModalContextValue | null>(
  null,
);

export function useSettingsModal(): SettingsModalContextValue {
  const value = useContext(SettingsModalContext);
  if (!value) {
    throw new Error('useSettingsModal must be used within SettingsModalProvider');
  }
  return value;
}

export const SettingsModalProvider: GenieType.FC<{ children: ReactNode }> = memo(
  ({ children }) => {
    const location = useLocation();
    const navigate = useNavigate();
    const lastSurfaceRef = useRef('/app');
    const [settingsState, setSettingsState] = useState<unknown>(undefined);
    const [session, setSession] = useState(0);

    const pathFromHash = hashToSettingsPath(location.hash);
    const pathFromUrl = location.pathname.startsWith('/app/settings')
      ? canonicalizeSettingsPath(location.pathname)
      : null;
    const settingsPath = pathFromHash ?? pathFromUrl ?? DEFAULT_SETTINGS_PATH;
    const open = pathFromHash !== null || pathFromUrl !== null;

    useLayoutEffect(() => {
      if (!isTransientSettingsPath(location.pathname)) {
        lastSurfaceRef.current = `${location.pathname}${location.search}`;
      }
    }, [location.pathname, location.search]);

    useLayoutEffect(() => {
      if (!location.pathname.startsWith('/app/settings')) {
        return;
      }
      if (location.state !== undefined) {
        setSettingsState(location.state);
      }
      const surface = splitPathSearch(lastSurfaceRef.current);
      navigate(
        {
          pathname: surface.pathname,
          search: surface.search,
          hash: settingsPathToHash(location.pathname),
        },
        { replace: true },
      );
    }, [location.pathname, location.state, navigate]);

    const openSettings = useCallback(
      (path?: string, state?: unknown) => {
        setSettingsState(state);
        setSession((current) => current + 1);
        const alreadyOpen = hashToSettingsPath(location.hash) !== null;
        const surface = isTransientSettingsPath(location.pathname)
          ? splitPathSearch(lastSurfaceRef.current)
          : { pathname: location.pathname, search: location.search };
        navigate(
          {
            pathname: surface.pathname,
            search: surface.search,
            hash: settingsPathToHash(path),
          },
          { replace: alreadyOpen },
        );
      },
      [location.hash, location.pathname, location.search, navigate],
    );

    const closeSettings = useCallback(() => {
      setSettingsState(undefined);
      if (hashToSettingsPath(location.hash) === null) {
        return;
      }
      navigate(
        {
          pathname: location.pathname,
          search: location.search,
          hash: '',
        },
        { replace: true },
      );
    }, [location.hash, location.pathname, location.search, navigate]);

    const value = useMemo(
      () => ({
        open,
        settingsPath,
        settingsState,
        session,
        openSettings,
        closeSettings,
        setSettingsState,
      }),
      [
        closeSettings,
        open,
        openSettings,
        session,
        settingsPath,
        settingsState,
      ],
    );

    return (
      <SettingsModalContext.Provider value={value}>
        {children}
      </SettingsModalContext.Provider>
    );
  },
);

SettingsModalProvider.displayName = 'SettingsModalProvider';
