import { Component, lazy, Suspense, useEffect, useState, type ReactNode } from 'react';
import { Alert, App as AntApp, Button, ConfigProvider, Flex, Layout, Menu, Spin, Typography } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { openSession, type Session } from '../auth/session';
const RolesPage = lazy(() => import('../features/iam/RolesPage').then(module => ({ default: module.RolesPage })));
import { ApiError } from '../api/client';
const theme = { token: { colorPrimary: '#1D4ED8', colorPrimaryHover: '#1E40AF', colorSuccess: '#047857',
  colorWarning: '#B45309', colorError: '#B91C1C', colorInfo: '#4338CA', colorBgLayout: '#EEF1F5',
  colorBgContainer: '#FFFFFF', colorText: '#111827', colorTextSecondary: '#4B5563', colorBorder: '#CBD5E1', borderRadius: 6 } };
/** 渲染错误不展示内部异常，避免错误组件泄漏业务数据。 */
class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() { return this.state.failed ? <Alert type="error" title="页面暂时无法显示" action={<Button href="/workbench/">重新加载</Button>} /> : this.props.children; }
}
function Workbench() {
  const [session, setSession] = useState<Session>();
  const [error, setError] = useState<Error>();
  useEffect(() => {
    let active = true;
    openSession().then(value => { if (active) setSession(value); })
      .catch((cause: unknown) => { if (active) setError(cause instanceof Error ? cause : new Error('工作台加载失败')); });
    return () => { active = false; };
  }, []);
  if (error) return <main className="session-panel"><Alert showIcon type={error instanceof ApiError && error.status === 401 ? 'info' : 'error'}
    title={error.message} action={<Button type="primary" href="/login">前往登录</Button>} /></main>;
  if (!session) return <main className="session-panel"><Spin tip="正在验证登录身份"><div className="loading-space" /></Spin></main>;
  const permitted = session.identity.permissions.includes('iam:role:read');
  async function logout() {
    await session!.logout(); setSession(undefined); location.replace('/login');
  }
  return <Layout className="workbench">
    <Layout.Sider theme="light" width={216}><div className="brand">ERP <span>业务工作台</span></div>
      <Menu selectedKeys={['roles']} items={permitted ? [{ key: 'roles', label: <a href="#/iam/roles">角色目录</a> }] : []} />
    </Layout.Sider>
    <Layout>
      <Layout.Header className="header"><Flex align="center" justify="space-between" gap={16}>
        <Typography.Text>租户 {session.identity.tenantId} · 用户 {session.identity.userId}</Typography.Text>
        <Button onClick={() => void logout()}>退出当前 ERP 登录</Button>
      </Flex></Layout.Header>
      <Layout.Content className="content"><Suspense fallback={<Spin aria-label="加载页面" />}><Routes>
        <Route path="/iam/roles" element={<RolesPage client={session.client} permitted={permitted} />} />
        <Route path="/" element={<Navigate to="/iam/roles" replace />} />
        <Route path="*" element={<Alert type="warning" title="找不到此页面" action={<Button href="#/iam/roles">返回角色目录</Button>} />} />
      </Routes></Suspense></Layout.Content>
    </Layout>
  </Layout>;
}
export default function App() {
  return <ConfigProvider theme={theme} locale={zhCN}><AntApp><ErrorBoundary><HashRouter><Workbench /></HashRouter></ErrorBoundary></AntApp></ConfigProvider>;
}
