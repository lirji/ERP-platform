/* 真实本地OIDC与工作台验证：凭据只在内存使用，日志不输出页面URL/token或密码。 */
const { chromium, expect } = require('../erp-web/node_modules/@playwright/test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const assert = require('node:assert/strict');
const base = process.env.ERP_SMOKE_BASE || 'http://localhost:18500';
const scopeCheck = process.env.ERP_SMOKE_SCOPE === '1';
let step = '登录'; let diagnosticPage;
(async () => {
  assert(['http://localhost:18500', 'http://localhost:8500'].includes(base));
  const credentials = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config/erp-platform/oidc-local.json'), 'utf8'));
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 1365, height: 900 } });
    const page = await context.newPage(); diagnosticPage = page; page.setDefaultTimeout(10000);
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
    step = '新建表单';
    const code = 'FBL_BROWSER_' + Date.now();
    await page.getByRole('button', { name: '新建角色', exact: true }).click();
    await page.getByLabel('角色编码', { exact: true }).fill(code);
    await page.getByLabel('角色名称', { exact: true }).fill('浏览器验证角色');
    step = '模拟响应丢失';
    let dropped = false;
    await page.route('**/api/v1/iam/roles', async route => {
      if (route.request().method() === 'POST' && !dropped) {
        dropped = true;
        const response = await route.fetch(); assert.equal(response.status(), 201);
        await route.abort('failed');
      } else await route.continue();
    });
    await page.getByRole('button', { name: '创建角色', exact: true }).click();
    step = '错误提示可见';
    await page.getByRole('dialog', { name: '新建普通角色' }).getByText('请求未能完成，请核对连接后使用原操作重试', { exact: true }).waitFor({ state: 'visible' });
    assert.equal(await page.getByLabel('角色编码', { exact: true }).inputValue(), code);
    await page.getByRole('button', { name: '创建角色', exact: true }).click();
    step = '重放关闭表单';
    await page.getByRole('dialog', { name: '新建普通角色' }).waitFor({ state: 'hidden' });
    step = '搜索新角色';
    await page.getByLabel('搜索角色', { exact: true }).fill(code);
    step = '搜索新角色';
    await page.getByLabel('搜索角色', { exact: true }).press('Enter');
    await page.getByRole('cell', { name: code, exact: true }).waitFor();
    await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(1);
    let treeReply;
    if (scopeCheck) { treeReply = page.waitForResponse(r => r.url().endsWith('/orgs/tree')); void treeReply.catch(() => {}); }
    step = '打开管理';
    await page.getByRole('button', { name: '管理角色 ' + code, exact: true }).click();
    await page.getByRole('dialog').waitFor();
    step = '选择权限';
    await page.getByLabel('可用权限', { exact: true }).fill('查看采购单');
    await page.getByText('采购 · 查看采购单', { exact: true }).last().click();
    step = '保存权限';
    await page.getByRole('heading', { name: '功能权限', exact: true }).click();
    const [assigned] = await Promise.all([page.waitForResponse(r => r.url().endsWith('/permissions') && r.request().method() === 'PUT'), page.getByRole('button', { name: '保存权限', exact: true }).click()]);
    assert.equal(assigned.status(), 200);
    const saved = await assigned.json(); assert.deepEqual(saved.permissions, ['purchase:order:read']);
    if (scopeCheck) {
      step = '配置公司范围';
      const orgs = await (await treeReply).json();
      const options = [];
      function flatten(nodes, parent = '') { for (const node of nodes) { const label = parent ? parent + ' / ' + node.name : node.name; if (node.type === 'COMPANY' && node.enabled) options.push({ id: node.id, label }); flatten(node.children, label); } }
      flatten(orgs); assert(options.length > 0); const company = options[0];
      await page.getByLabel('主体范围', { exact: true }).click();
      await page.getByText('指定公司', { exact: true }).last().click();
      await page.getByRole('combobox', { name: /指定公司/ }).click();
      await page.getByRole('combobox', { name: /指定公司/ }).fill(company.label);
      await page.getByText(company.label, { exact: true }).last().click();
      await page.getByRole('heading', { name: '数据范围', exact: true }).click();
      const [scopeReply] = await Promise.all([page.waitForResponse(r => r.url().endsWith('/data-scope') && r.request().method() === 'PUT'), page.getByRole('button', { name: '保存数据范围', exact: true }).click()]);
      assert.equal(scopeReply.status(), 200);
      const scoped = await scopeReply.json(); assert.equal(scoped.scope.type, 'SPECIFIED_COMPANY'); assert.deepEqual(scoped.scope.companyIds, [company.id]);
      await page.locator('.ant-drawer-close').click();
      await page.getByRole('dialog').waitFor({ state: 'hidden' });
      await page.getByRole('button', { name: '管理角色 ' + code, exact: true }).click();
      await page.getByRole('dialog').waitFor();
      await expect(page.getByRole('dialog').getByText(company.label, { exact: true })).toBeVisible();
    }
    step = '停用角色';
    await page.getByRole('switch', { name: '启用角色' }).click();
    const [metadataReply] = await Promise.all([page.waitForResponse(r => r.url().endsWith('/roles/' + saved.id) && r.request().method() === 'PUT'), page.getByRole('button', { name: '保存基本信息', exact: true }).click()]);
    assert.equal((await metadataReply.json()).enabled, false);
    // 仅停用此前本工具失败运行留下的无权限测试角色，保留审计，不删除数据。
    await page.evaluate(async () => {
      const storageKey = Object.keys(sessionStorage).find(k => k.startsWith('oidc.user:'));
      const token = JSON.parse(sessionStorage.getItem(storageKey)).access_token;
      const headers = { Authorization: 'Bearer ' + token };
      const reply = await fetch('/api/v1/iam/role-directory?q=FBL_BROWSER_&size=200', { headers });
      if (!reply.ok) throw new Error('测试角色查询失败');
      const result = await reply.json();
      if (result.total > 200) throw new Error('测试角色数量超过清理边界');
      for (const role of result.list) {
        if (/^FBL_BROWSER_[0-9]+$/.test(role.code) && role.name === '浏览器验证角色' && role.enabled && !role.protected && role.permissions.every(p => p === 'purchase:order:read')) {
          const response = await fetch('/api/v1/iam/roles/' + role.id, { method: 'PUT', headers: { ...headers, 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ expectedVersion: role.version, name: role.name, enabled: false }) });
          if (!response.ok) throw new Error('测试角色停用失败');
        }
      }
    });
    await page.screenshot({ path: scopeCheck ? '/tmp/erp-fbl-s2a-workbench.png' : '/tmp/erp-fbl-s1-workbench.png', fullPage: true });
    step = '关闭侧栏';
    await page.locator('.ant-drawer-close').click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    await page.getByRole('button', { name: '退出当前 ERP 登录' }).click();
    await page.waitForURL(base + '/login');
    assert.equal(await page.evaluate(() => Object.keys(sessionStorage).filter(k => k.startsWith('oidc.user:')).length), 0);
    await page.goto(base + '/workbench/');
    await page.getByRole('link', { name: '前往登录' }).waitFor();
    console.log(JSON.stringify({ pkce: 'PASS', sharedSession: 'PASS', rolesApi: 200, createLostResponseReplay: 'PASS', permissions: 'PASS', scope: scopeCheck ? 'PASS' : 'NOT_RUN', disable: 'PASS', expiredTokenRefresh: 'PASS', keyboardRefresh: 'PASS', logout: 'PASS', noSession: 'PASS' }));
    await context.close();
  } finally { if (diagnosticPage && !diagnosticPage.isClosed() && diagnosticPage.url().startsWith(base)) await diagnosticPage.screenshot({ path: '/tmp/erp-fbl-s1-diagnostic.png', fullPage: true }).catch(() => {}); await browser.close(); }
})().catch(error => { console.error('角色浏览器验证失败步骤：' + step); if (step !== '登录') console.error(String(error.message).split('\n')[0].replace(/https?:\/\/\S+/g,'[URL]')); process.exitCode = 1; });
