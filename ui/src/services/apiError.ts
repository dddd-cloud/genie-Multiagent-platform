export class MvpApiError extends Error {
  constructor(
    public httpStatus: number,
    public code: string,
    message: string,
    public data: unknown = null,
  ) {
    super(message);
    this.name = 'MvpApiError';
  }
}

export class LegacyApiError extends Error {
  constructor(
    public httpStatus: number,
    public code: number,
    message: string,
    public data: unknown = null,
  ) {
    super(message);
    this.name = 'LegacyApiError';
  }
}
