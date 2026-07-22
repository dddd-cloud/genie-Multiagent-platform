export const USER_ROLES = ['ADMIN', 'USER'] as const;
export type UserRole = (typeof USER_ROLES)[number];

export const USER_STATUSES = ['ACTIVE', 'DISABLED'] as const;
export type UserStatus = (typeof USER_STATUSES)[number];

export interface LoginRequest {
  username: string;
  password: string;
}

export interface CsrfTokenResponse {
  headerName: 'X-XSRF-TOKEN';
  parameterName: '_csrf';
  token: string;
}

export interface UserResponse {
  id: string;
  username: string;
  displayName: string;
  role: UserRole;
}

export interface AdminUserResponse extends UserResponse {
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}
