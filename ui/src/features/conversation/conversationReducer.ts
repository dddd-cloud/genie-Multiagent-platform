import type { ConversationListItem } from '@/contracts';

export interface ConversationListState {
  items: ConversationListItem[];
  page: number;
  hasMore: boolean;
  loading: boolean;
  loadingMore: boolean;
  error: string | null;
}

export type ConversationListAction =
  | { type: 'LOAD_START'; more?: boolean }
  | {
      type: 'LOAD_SUCCESS';
      items: ConversationListItem[];
      page: number;
      hasMore: boolean;
    }
  | {
      type: 'LOAD_FAILURE';
      error: string;
      more?: boolean;
    }
  | {
      type: 'APPEND_SUCCESS';
      items: ConversationListItem[];
      page: number;
      hasMore: boolean;
    }
  | { type: 'UPSERT'; item: ConversationListItem }
  | { type: 'REMOVE'; id: string }
  | { type: 'RESET' };

export const initialConversationListState: ConversationListState = {
  items: [],
  page: 0,
  hasMore: true,
  loading: false,
  loadingMore: false,
  error: null,
};

export function conversationReducer(
  state: ConversationListState,
  action: ConversationListAction,
): ConversationListState {
  switch (action.type) {
    case 'LOAD_START':
      return {
        ...state,
        error: null,
        loading: action.more ? state.loading : true,
        loadingMore: !!action.more,
      };
    case 'LOAD_SUCCESS':
      return {
        ...state,
        loading: false,
        loadingMore: false,
        error: null,
        items: action.items,
        page: action.page,
        hasMore: action.hasMore,
      };
    case 'LOAD_FAILURE':
      return {
        ...state,
        loading: false,
        loadingMore: false,
        error: action.error,
      };
    case 'APPEND_SUCCESS': {
      const seen = new Set(state.items.map((item) => item.id));
      const appended = action.items.filter((item) => !seen.has(item.id));
      return {
        ...state,
        loading: false,
        loadingMore: false,
        error: null,
        items: [...state.items, ...appended],
        page: action.page,
        hasMore: action.hasMore,
      };
    }
    case 'UPSERT': {
      const index = state.items.findIndex((item) => item.id === action.item.id);
      if (index === -1) {
        return {
          ...state,
          items: [action.item, ...state.items],
        };
      }
      const next = state.items.slice();
      next[index] = action.item;
      return { ...state, items: next };
    }
    case 'REMOVE':
      return {
        ...state,
        items: state.items.filter((item) => item.id !== action.id),
      };
    case 'RESET':
      return { ...initialConversationListState };
    default:
      return state;
  }
}
