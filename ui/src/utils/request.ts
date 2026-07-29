import axios, { AxiosInstance, AxiosResponse } from 'axios';

/** Same-origin Axios client — browser never bakes absolute backend URLs. */
const request: AxiosInstance = axios.create({
  baseURL: '',
  timeout: 10000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

request.interceptors.request.use(
  (config) => config,
  (error) => Promise.reject(error),
);

/**
 * Success interceptor returns raw `response.data` only.
 * Business unpacking lives in Legacy (`services/index`) or MVP (`services/mvp`).
 */
request.interceptors.response.use(
  (response: AxiosResponse) => response.data,
  (error) => Promise.reject(error),
);

export default request;
