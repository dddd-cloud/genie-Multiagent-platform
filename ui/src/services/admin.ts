import type {
  AdminUserResponse,
  PageResponse,
  UsageRangeQuery,
  UsageSummaryResponse,
  UsageUserRow,
  UserRole,
  UserStatus,
} from '@/contracts';
import { requestMvp } from './mvp';

const USERS_URL = '/api/v1/admin/users';
const USAGE_URL = '/api/v1/admin/usage';

export interface AdminUserQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
  role?: UserRole;
  status?: UserStatus;
}

export interface CreateAdminUserBody {
  username: string;
  displayName: string;
  password: string;
  role: UserRole;
}

export function listAdminUsers(
  query: AdminUserQuery = {},
  signal?: AbortSignal,
): Promise<PageResponse<AdminUserResponse> | null> {
  return requestMvp<PageResponse<AdminUserResponse>>({
    method: 'GET',
    url: USERS_URL,
    params: {
      page: query.page ?? 1,
      pageSize: query.pageSize ?? 20,
      keyword: query.keyword?.trim() || undefined,
      role: query.role,
      status: query.status,
    },
    signal,
  });
}

export function getAdminUser(
  userId: string,
  signal?: AbortSignal,
): Promise<AdminUserResponse | null> {
  return requestMvp<AdminUserResponse>({
    method: 'GET',
    url: `${USERS_URL}/${encodeURIComponent(userId)}`,
    signal,
  });
}

export function createAdminUser(
  body: CreateAdminUserBody,
  signal?: AbortSignal,
): Promise<AdminUserResponse | null> {
  return requestMvp<AdminUserResponse>({
    method: 'POST',
    url: USERS_URL,
    data: body,
    signal,
  });
}

export function updateAdminUserStatus(
  userId: string,
  status: UserStatus,
  signal?: AbortSignal,
): Promise<AdminUserResponse | null> {
  return requestMvp<AdminUserResponse>({
    method: 'PATCH',
    url: `${USERS_URL}/${encodeURIComponent(userId)}/status`,
    data: { status },
    signal,
  });
}

export function resetAdminUserPassword(
  userId: string,
  newPassword: string,
  signal?: AbortSignal,
): Promise<null> {
  return requestMvp<null>({
    method: 'POST',
    url: `${USERS_URL}/${encodeURIComponent(userId)}/reset-password`,
    data: { newPassword },
    signal,
  });
}

export function fetchAdminUsageSummary(
  range?: UsageRangeQuery,
  signal?: AbortSignal,
): Promise<UsageSummaryResponse | null> {
  return requestMvp<UsageSummaryResponse>({
    method: 'GET',
    url: `${USAGE_URL}/summary`,
    params: range,
    signal,
  });
}

export function listAdminUsageUsers(
  query: UsageRangeQuery & { page?: number; pageSize?: number } = {},
  signal?: AbortSignal,
): Promise<PageResponse<UsageUserRow> | null> {
  return requestMvp<PageResponse<UsageUserRow>>({
    method: 'GET',
    url: `${USAGE_URL}/users`,
    params: {
      from: query.from,
      to: query.to,
      page: query.page ?? 1,
      pageSize: query.pageSize ?? 20,
    },
    signal,
  });
}
