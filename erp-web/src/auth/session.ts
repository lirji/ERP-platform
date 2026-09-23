import { UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { ApiError, createApiClient } from '../api/client';
export interface Identity {
  userId: number; tenantId: number; companyId: number; orgPath: string;
  timezone: string; permissions: string[]; dataScope: string;
}
interface PublicConfig { enabled: boolean; issuer: string; clientId: string; publicBaseUrl: string }
/** 与既有登录页共享精确 authority/clientId/storage，避免创建第二套登录会话。 */
export async function openSession() {
  const response = await fetch('/auth/config', { cache: 'no-store' });
  if (!response.ok) throw new Error('登录配置读取失败，请稍后重试');
  const config: PublicConfig = await response.json();
  if (!config.enabled) throw new Error('统一登录尚未配置，请联系管理员');
  if (config.publicBaseUrl !== location.origin) throw new Error('请从已登记的 ERP 地址进入工作台');
  const manager = new UserManager({
    authority: config.issuer, client_id: config.clientId,
    redirect_uri: config.publicBaseUrl + '/auth/callback', response_type: 'code',
    scope: 'openid profile offline_access', loadUserInfo: false, automaticSilentRenew: false,
    userStore: new WebStorageStateStore({ store: sessionStorage }),
    stateStore: new WebStorageStateStore({ store: sessionStorage }),
  });
  let renewing: Promise<void> | undefined;
  async function refresh() {
    if (!renewing) renewing = (async () => {
      const current = await manager.getUser();
      if (!current?.refresh_token) throw new ApiError(401, '登录已失效，请重新登录');
      const renewed = await manager.signinSilent();
      if (!renewed?.access_token) throw new ApiError(401, '登录已失效，请重新登录');
    })().finally(() => { renewing = undefined; });
    return renewing;
  }
  const client = createApiClient({
    async token() {
      let user = await manager.getUser();
      if (user?.expired) {
        try { await refresh(); user = await manager.getUser(); }
        catch { await manager.removeUser(); throw new ApiError(401, '登录已失效，请重新登录'); }
      }
      if (!user?.access_token) throw new ApiError(401, '请先通过统一身份平台登录');
      return user.access_token;
    },
    refresh, clear: () => manager.removeUser(),
  });
  const identity = await client.get<Identity>('/api/v1/iam/me');
  if (![identity.userId, identity.tenantId, identity.companyId].every(Number.isSafeInteger)
      || !Array.isArray(identity.permissions) || !identity.permissions.every(p => typeof p === 'string'))
    throw new Error('身份响应格式不兼容，请联系管理员');
  return { client, identity, logout: () => manager.removeUser() };
}
export type Session = Awaited<ReturnType<typeof openSession>>;
