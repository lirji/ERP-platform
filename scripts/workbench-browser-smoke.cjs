/* 真实本地OIDC与工作台验证：凭据只在内存使用，日志不输出页面URL/token或密码。 */
const { chromium } = require('../erp-web/node_modules/@playwright/test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const assert = require('node:assert/strict');
const base = process.env.ERP_SMOKE_BASE || 'http://localhost:18500';
(async () => {
  assert(['http://localhost:18500', 'http://localhost:8500'].includes(base));
  const credentials = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config/erp-platform/oidc-local.json'), 'utf8'));
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 1365, height: 900 } });
    const page = await context.newPage();
    await page.goto(base + '/workbench/');
    await page.getByRole('link', { name: '前往登录' }).click();
    await page.getByRole('button', { name: '通过统一身份平台登录' }).click();
    await page.waitForURL('http://localhost:8000/**');
    const auth = new URL(page.url());
    assert.equal(auth.searchParams.get('client_id'), 'erp-platform');
    assert.equal(auth.searchParams.get('code_challenge_method'), 'S256');
    await page.locator('#username').fill(credentials.users.demo_operator.username);
    await page.locator('#password').fill(credentials.users.demo_operator.password);
    await page.getByRole('button', { name: 'Sign In', exact: true }).click();
    await page.getByRole('link', { name: '进入业务工作台' }).click();
    await page.getByRole('heading', { name: '角色目录', exact: true }).waitFor();
    await page.locator('tbody tr[data-row-key]').first().waitFor();
    const before = await page.evaluate(() => {
      const key = Object.keys(sessionStorage).find(k => k.startsWith('oidc.user:'));
      const user = JSON.parse(sessionStorage.getItem(key));
      user.expires_at = 1; sessionStorage.setItem(key, JSON.stringify(user));
      return { refresh: !!user.refresh_token };
    });
    assert(before.refresh);
    await page.reload();
    await page.locator('tbody tr[data-row-key]').first().waitFor();
    const refreshed = await page.evaluate(() => {
      const key = Object.keys(sessionStorage).find(k => k.startsWith('oidc.user:'));
      return JSON.parse(sessionStorage.getItem(key)).expires_at > Date.now() / 1000;
    });
    assert(refreshed);
    const refresh = page.getByRole('button', { name: '刷新角色', exact: true });
    await refresh.focus();
    const reply = page.waitForResponse(r => r.url().startsWith(base + '/api/v1/iam/role-directory?'));
    await page.keyboard.press('Enter'); assert.equal((await reply).status(), 200);
    await page.screenshot({ path: '/tmp/erp-fbl-s0-workbench.png', fullPage: true });
    await page.getByRole('button', { name: '退出当前 ERP 登录' }).click();
    await page.waitForURL(base + '/login');
    assert.equal(await page.evaluate(() => Object.keys(sessionStorage).filter(k => k.startsWith('oidc.user:')).length), 0);
    await page.goto(base + '/workbench/');
    await page.getByRole('link', { name: '前往登录' }).waitFor();
    console.log(JSON.stringify({ pkce: 'PASS', sharedSession: 'PASS', rolesApi: 200, expiredTokenRefresh: 'PASS', keyboardRefresh: 'PASS', logout: 'PASS', noSession: 'PASS' }));
    await context.close();
  } finally { await browser.close(); }
})().catch(() => { console.error('工作台浏览器验证失败；不输出凭据或授权页面详情。'); process.exitCode = 1; });
