/* 本地真实 Casdoor 授权码 + PKCE 验证；凭据只读私密文件，令牌不离开浏览器上下文。 */
const {chromium} = require('playwright');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const assert = require('node:assert/strict');
const base = process.env.ERP_SMOKE_BASE || 'http://localhost:18500';
const privateFile = process.env.ERP_IAM_CREDENTIALS || path.join(os.homedir(), '.config/erp-platform/oidc-local.json');
const credentials = JSON.parse(fs.readFileSync(privateFile, 'utf8'));
(async () => {
  assert(['http://localhost:18500', 'http://localhost:8500'].includes(base), '只允许本地验证目标');
  const browser = await chromium.launch({channel:'chrome', headless:true});
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto(base + '/login');
    await page.getByRole('button', {name:'通过统一身份平台登录'}).click();
    await page.waitForURL('http://localhost:8000/**');
    const auth = new URL(page.url());
    assert.equal(auth.searchParams.get('client_id'), 'erp-platform');
    assert.equal(auth.searchParams.get('code_challenge_method'), 'S256');
    assert(auth.searchParams.get('code_challenge'));
    assert(auth.searchParams.get('state'));
    await page.locator('#username').fill(credentials.users.demo_operator.username);
    await page.locator('#password').fill(credentials.users.demo_operator.password);
    await page.getByRole('button', {name:'Sign In', exact:true}).click();
    await page.waitForURL(base + '/login', {timeout:20000});
    await page.getByText('登录成功，ERP 身份与权限已验证。', {exact:true}).waitFor();
    const results = await page.evaluate(async () => {
      const key = Object.keys(sessionStorage).find(k => k.startsWith('oidc.user:'));
      const user = JSON.parse(sessionStorage.getItem(key));
      const token = user.access_token;
      const req = async (url, headers={}) => {
        const res = await fetch(url, {headers});
        return {status:res.status, body:await res.json()};
      };
      const identity = await req('/api/v1/iam/me', {Authorization:'Bearer '+token, 'X-Tenant-Code':'spoof', 'X-Username':'admin'});
      const parts = token.split('.');
      parts[2] = (parts[2][0] === 'A' ? 'B' : 'A') + parts[2].slice(1);
      return {
        tenant:identity.body.tenantId,
        valid:identity.status,
        roles:(await req('/api/v1/iam/roles', {Authorization:'Bearer '+token})).status,
        missing:(await req('/api/v1/iam/me')).status,
        tampered:(await req('/api/v1/iam/me', {Authorization:'Bearer '+parts.join('.')})).status,
        refresh:!!user.refresh_token
      };
    });
    assert.equal(results.tenant,990001);
    assert.equal(results.valid,200); assert.equal(results.roles,200);
    assert.equal(results.missing,401); assert.equal(results.tampered,401);
    const renewed = await page.evaluate(async () => {
      const c = await (await fetch('/auth/config')).json();
      const manager = new oidc.UserManager({authority:c.issuer,client_id:c.clientId,
        redirect_uri:c.publicBaseUrl+'/auth/callback',response_type:'code',scope:'openid profile offline_access',
        loadUserInfo:false,automaticSilentRenew:false,userStore:new oidc.WebStorageStateStore({store:sessionStorage})});
      const user = await manager.signinSilent();
      return (await fetch('/api/v1/iam/me',{headers:{Authorization:'Bearer '+user.access_token}})).status;
    });
    assert.equal(renewed,200);
    await page.getByRole('button', {name:'退出当前 ERP 登录'}).click();
    await page.getByText('使用统一身份平台账号登录 ERP。', {exact:true}).waitFor();
    assert.equal(await page.evaluate(() => Object.keys(sessionStorage).filter(k=>k.startsWith('oidc.user:')).length),0);
    await page.goto(base + '/auth/callback?code=invalid&state=invalid');
    await page.getByText('登录回调无效或已过期，请重新登录。', {exact:true}).waitFor();
    assert.equal(page.url(),base+'/login');
    assert(await page.getByRole('button', {name:'通过统一身份平台登录'}).isEnabled());
    await context.close();
    const deniedContext = await browser.newContext();
    const denied = await deniedContext.newPage();
    await denied.goto(base+'/login');
    await denied.getByRole('button',{name:'通过统一身份平台登录'}).click();
    await denied.locator('#username').fill(credentials.users.unbound_user.username);
    await denied.locator('#password').fill(credentials.users.unbound_user.password);
    await denied.getByRole('button',{name:'Sign In',exact:true}).click();
    await denied.getByText('身份未绑定、账号已停用或登录已失效，请联系管理员。',{exact:true}).waitFor();
    await deniedContext.close();
    console.log(JSON.stringify({pkce:'PASS',boundLogin:'PASS',tenantIsolation:'PASS',valid:results.valid,
      missing:results.missing,tampered:results.tampered,unbound:'REJECTED',localLogout:'PASS',invalidState:'REJECTED',refreshToken:results.refresh,renewed:renewed}));
  } finally { await browser.close(); }
})().catch(() => { console.error('OIDC browser smoke FAILED；不输出浏览器异常中的授权码或令牌。'); process.exitCode=1; });
