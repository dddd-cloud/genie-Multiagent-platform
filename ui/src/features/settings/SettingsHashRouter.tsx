import { memo, useCallback, useContext, useMemo, type ReactNode } from 'react';
import {
  NavigationType,
  parsePath,
  resolvePath,
  UNSAFE_LocationContext,
  UNSAFE_NavigationContext,
  UNSAFE_RouteContext,
  useLocation,
  useNavigate,
  type Location,
  type Navigator,
  type To,
} from 'react-router-dom';
import { useSettingsModal } from './SettingsModalContext';
import {
  canonicalizeSettingsPath,
  hashToSettingsPath,
  settingsPathToHash,
} from './settingsHash';

function toSettingsPathname(to: To, currentPathname: string): string {
  if (typeof to === 'string' && to.startsWith('#')) {
    return hashToSettingsPath(to) ?? canonicalizeSettingsPath(currentPathname);
  }
  const target = typeof to === 'string' ? parsePath(to) : to;
  return canonicalizeSettingsPath(resolvePath(target, currentPathname).pathname);
}

const EMPTY_ROUTE_CONTEXT = {
  outlet: null,
  matches: [],
  isDataRoute: false,
};

const SettingsHashRouter: GenieType.FC<{ children: ReactNode }> = memo(
  ({ children }) => {
    const outerLocation = useLocation();
    const outerNavigate = useNavigate();
    const parentNavigation = useContext(UNSAFE_NavigationContext);
    const { settingsPath, settingsState, session, setSettingsState } =
      useSettingsModal();

    const innerLocation = useMemo<Location>(
      () => ({
        pathname: settingsPath,
        search: '',
        hash: '',
        state: settingsState,
        key: `settings-${session}`,
      }),
      [session, settingsPath, settingsState],
    );

    const applyHash = useCallback(
      (to: To, state: unknown, replace: boolean) => {
        if (state !== undefined) {
          setSettingsState(state);
        }
        outerNavigate(
          {
            pathname: outerLocation.pathname,
            search: outerLocation.search,
            hash: settingsPathToHash(toSettingsPathname(to, settingsPath)),
          },
          { replace },
        );
      },
      [
        outerLocation.pathname,
        outerLocation.search,
        outerNavigate,
        setSettingsState,
        settingsPath,
      ],
    );

    const navigator = useMemo<Navigator>(
      () => ({
        createHref: (to) =>
          settingsPathToHash(toSettingsPathname(to, settingsPath)),
        encodeLocation: (to) => ({
          pathname: toSettingsPathname(to, settingsPath),
          search: '',
          hash: '',
        }),
        go: (delta) => {
          window.history.go(delta);
        },
        push: (to, state) => {
          applyHash(to, state, true);
        },
        replace: (to, state) => {
          applyHash(to, state, true);
        },
      }),
      [applyHash, settingsPath],
    );

    const navigation = useMemo(
      () => ({
        ...parentNavigation,
        navigator,
        static: false,
      }),
      [navigator, parentNavigation],
    );

    return (
      <UNSAFE_LocationContext.Provider
        value={{ location: innerLocation, navigationType: NavigationType.Replace }}
      >
        <UNSAFE_NavigationContext.Provider value={navigation}>
          <UNSAFE_RouteContext.Provider value={EMPTY_ROUTE_CONTEXT}>
            {children}
          </UNSAFE_RouteContext.Provider>
        </UNSAFE_NavigationContext.Provider>
      </UNSAFE_LocationContext.Provider>
    );
  },
);

SettingsHashRouter.displayName = 'SettingsHashRouter';

export default SettingsHashRouter;
