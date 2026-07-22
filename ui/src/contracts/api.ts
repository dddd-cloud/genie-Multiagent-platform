export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T | null;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  hasMore: boolean;
}
