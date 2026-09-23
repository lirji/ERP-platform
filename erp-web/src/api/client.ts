/** API 失败与无数据严格区分，不将响应正文或令牌输出到日志。 */
export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly traceId?: string) {
    super(message);
  }
}
export interface TokenSession {
  token(): Promise<string>;
  refresh(): Promise<void>;
  clear(): Promise<void>;
}
export function createApiClient(session: TokenSession, transport: typeof fetch = fetch) {
  let renewal: Promise<void> | undefined;
  async function renew() {
    if (!renewal) renewal = session.refresh().finally(() => { renewal = undefined; });
    return renewal;
  }
  return {
    async get<T>(path: string, signal?: AbortSignal): Promise<T> {
      if (!path.startsWith('/api/v1/')) throw new Error('仅允许同源业务接口');
      for (let attempt = 0; attempt < 2; attempt++) {
        const token = await session.token();
        const response = await transport(path, { signal, cache: 'no-store', headers: { Authorization: `Bearer ${token}` } });
        if (response.status === 401 && attempt === 0) {
          try { await renew(); } catch { await session.clear(); throw new ApiError(401, '登录已失效，请重新登录'); }
          continue;
        }
        if (!response.ok) {
          if (response.status === 401) await session.clear();
          const payload = await response.json().catch(() => ({}));
          const message = response.status === 401 ? '登录已失效，请重新登录'
            : response.status === 403 ? '没有访问此功能的权限'
            : response.status === 404 ? '找不到该记录'
            : response.status >= 500 ? '服务暂时不可用，请稍后重试' : '请求失败，请刷新后重试';
          throw new ApiError(response.status, message, typeof payload.traceId === 'string' ? payload.traceId : undefined);
        }
        return await response.json() as T;
      }
      throw new ApiError(401, '登录已失效，请重新登录');
    },
  };
}
export type ApiClient = ReturnType<typeof createApiClient>;
