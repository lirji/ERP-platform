/* 只做登录与本地授权身份验证；协议状态、PKCE 和刷新由 oidc-client-ts 管理。 */
(async () => {
  const status = document.getElementById('status');
  const login = document.getElementById('login');
  const logout = document.getElementById('logout');
  const identity = document.getElementById('identity');
  try {
    const configResponse = await fetch('/auth/config', {cache: 'no-store'});
    if (!configResponse.ok) throw new Error('登录配置读取失败');
    const config = await configResponse.json();
    if (!config.enabled) { status.textContent = '统一登录尚未配置，请联系管理员。'; return; }
    if (location.origin !== config.publicBaseUrl) {
      status.textContent = '请从登记的 ERP 地址进入：' + config.publicBaseUrl + '/login'; return;
    }
    const manager = new oidc.UserManager({
      authority: config.issuer, client_id: config.clientId,
      redirect_uri: config.publicBaseUrl + '/auth/callback',
      response_type: 'code', scope: 'openid profile offline_access',
      loadUserInfo: false, automaticSilentRenew: false,
      userStore: new oidc.WebStorageStateStore({store: sessionStorage}),
      stateStore: new oidc.WebStorageStateStore({store: sessionStorage})
    });
    login.onclick = async () => {
      login.disabled = true;
      try { await manager.signinRedirect(); }
      catch { status.textContent = '无法连接统一登录平台，请稍后重试。'; login.disabled = false; }
    };
    // 本地退出不擅自结束其他项目的 SSO 会话；不声称令牌在服务端已撤销。
    logout.onclick = async () => { await manager.removeUser(); location.replace('/login'); };
    if (location.pathname === '/auth/callback') {
      try { await manager.signinRedirectCallback(); }
      catch { await manager.removeUser(); throw new Error('登录回调无效或已过期，请重新登录。'); }
      finally { history.replaceState(null, '', '/login'); }
    }
    let user = await manager.getUser();
    if (user?.expired && user.refresh_token) {
      try { user = await manager.signinSilent(); } catch { await manager.removeUser(); user = null; }
    }
    login.disabled = false;
    if (!user || user.expired) { status.textContent = '使用统一身份平台账号登录 ERP。'; return; }
    let response = await fetch('/api/v1/iam/me', {headers: {Authorization: 'Bearer ' + user.access_token}, cache: 'no-store'});
    if (response.status === 401 && user.refresh_token) {
      try {
        user = await manager.signinSilent();
        response = await fetch('/api/v1/iam/me', {headers: {Authorization: 'Bearer ' + user.access_token}, cache: 'no-store'});
      } catch { await manager.removeUser(); throw new Error('登录已过期，请重新登录。'); }
    }
    if (!response.ok) { await manager.removeUser(); throw new Error('身份未绑定、账号已停用或登录已失效，请联系管理员。'); }
    const me = await response.json();
    status.textContent = '登录成功，ERP 身份与权限已验证。';
    identity.textContent = JSON.stringify(me, null, 2);
    identity.hidden = false; logout.hidden = false; login.hidden = true;
  } catch (error) {
    status.textContent = error.message || '登录失败，请重试。';
    login.disabled = !login.onclick;
  }
})();
