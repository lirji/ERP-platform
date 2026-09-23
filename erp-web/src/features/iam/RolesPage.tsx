import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Drawer, Empty, Flex, Form, Input, Modal, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ApiClient } from '../../api/client';
import { ApiError } from '../../api/client';
interface Scope { type: string; orgIds: string[]; companyIds: string[]; warehouseMode: string; warehouseIds: string[] }
interface Role { id: string; version: string; code: string; name: string; enabled: boolean; protected: boolean; permissions: string[]; scope: Scope | null; scopeState: string }
interface Page { list: Role[]; total: number; page: number; size: number }
interface Permission { code: string; label: string; group: string }
const scopeLabels: Record<string, string> = { SELF: '仅本人', DEPT: '本部门', DEPT_AND_BELOW: '本部门及下级', SPECIFIED_ORG: '指定组织', SPECIFIED_COMPANY: '指定公司', ALL: '本租户全部' };
function validRole(value: Role) {
  return typeof value?.id === 'string' && /^[1-9]\d*$/.test(value.id) && typeof value.version === 'string' && /^\d+$/.test(value.version)
    && typeof value.code === 'string' && typeof value.name === 'string' && typeof value.enabled === 'boolean' && typeof value.protected === 'boolean'
    && Array.isArray(value.permissions) && value.permissions.every(p => typeof p === 'string');
}
function failure(cause: unknown) { return cause instanceof ApiError ? cause : new Error('请求未能完成，请核对连接后使用原操作重试'); }
/** 分页、字段与命令绑定真实契约；一个编辑意图重试时保持相同幂等键。 */
export function RolesPage({ client, permitted, canManage = false }: { client: ApiClient; permitted: boolean; canManage?: boolean }) {
  const [rows, setRows] = useState<Role[]>([]); const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1); const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true); const [error, setError] = useState<Error>();
  const [revision, setRevision] = useState(0); const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState<Role>(); const [catalog, setCatalog] = useState<Permission[]>([]);
  const [pending, setPending] = useState(false); const [writeError, setWriteError] = useState<Error>();
  const [createForm] = Form.useForm(); const [metadataForm] = Form.useForm(); const [permissionForm] = Form.useForm();
  const command = useRef<{ signature: string; key: string } | undefined>(undefined);
  useEffect(() => {
    if (!permitted) return;
    const controller = new AbortController(); setLoading(true); setRows([]); setError(undefined);
    client.get<Page>(`/api/v1/iam/role-directory?page=${page}&size=20&sort=id&q=${encodeURIComponent(query)}`, controller.signal).then(data => {
      if (controller.signal.aborted) return;
      if (!Array.isArray(data.list) || !data.list.every(validRole) || !Number.isSafeInteger(data.total) || data.total < 0) throw new Error('响应格式错误');
      setRows(data.list); setTotal(data.total);
    }).catch((cause: unknown) => { if (!controller.signal.aborted) setError(failure(cause)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [client, permitted, page, query, revision]);
  async function edit(role: Role) {
    setPending(true); setWriteError(undefined); setError(undefined); command.current = undefined;
    try {
      const [detail, permissions] = await Promise.all([client.get<Role>(`/api/v1/iam/roles/${role.id}`), client.get<Permission[]>('/api/v1/iam/permissions')]);
      if (!validRole(detail) || !Array.isArray(permissions) || !permissions.every(p => typeof p.code === 'string' && typeof p.label === 'string')) throw new Error('响应格式错误');
      setCatalog(permissions); setSelected(detail); metadataForm.setFieldsValue(detail); permissionForm.setFieldsValue({ permissions: detail.permissions });
    } catch (cause) { setError(failure(cause)); } finally { setPending(false); }
  }
  async function save(path: string, method: 'POST' | 'PUT', body: unknown) {
    const signature = JSON.stringify({ path, method, body });
    if (command.current?.signature !== signature) command.current = { signature, key: crypto.randomUUID() };
    setPending(true); setWriteError(undefined);
    try {
      const role = await client.command<Role>(path, method, body, command.current.key);
      if (!validRole(role)) throw new Error('响应格式错误');
      command.current = undefined; setRevision(v => v + 1);
      if (creating) { setCreating(false); createForm.resetFields(); }
      else { setSelected(role); metadataForm.setFieldsValue(role); permissionForm.setFieldsValue({ permissions: role.permissions }); }
    } catch (cause) { setWriteError(failure(cause)); } finally { setPending(false); }
  }
  const writeAlert = writeError && <Alert type="error" showIcon title={writeError.message} description={writeError instanceof ApiError && writeError.status === 409 ? '关闭后重新打开角色以读取最新版本；不会覆盖他人修改。' : '失败时保留表单与幂等键，可再次提交。'} />;
  if (!permitted) return <Alert type="warning" showIcon title="没有角色查询权限" description="请联系管理员申请访问权限。" />;
  return <section aria-labelledby="roles-title">
    <Flex justify="space-between" align="center" gap={16}>
      <div><Typography.Title id="roles-title" level={2}>角色目录</Typography.Title><Typography.Paragraph type="secondary">维护当前租户的普通角色与功能权限。</Typography.Paragraph></div>
      <Space><Button aria-label="刷新角色" onClick={() => setRevision(v => v + 1)} loading={loading}>刷新角色</Button>
        {canManage && <Button type="primary" disabled={pending} onClick={() => { command.current = undefined; setWriteError(undefined); setCreating(true); }}>新建角色</Button>}</Space>
    </Flex>
    <Input.Search aria-label="搜索角色" placeholder="按编码或名称查询" maxLength={128} onSearch={q => { setPage(1); setQuery(q); }} style={{ maxWidth: 360, marginBottom: 16 }} />
    {error ? <Alert type="error" showIcon title={error.message} description={error instanceof ApiError && error.traceId ? `关联编号：${error.traceId}` : '请检查连接或重试。'}
      action={error instanceof ApiError && error.status === 401 ? <Button href="/login">重新登录</Button> : undefined} />
      : <Table<Role> rowKey="id" loading={loading} dataSource={rows} pagination={{ current: page, pageSize: 20, total, onChange: setPage, showSizeChanger: false }} scroll={{ x: 780 }}
        locale={{ emptyText: <Empty description="当前租户暂无角色" /> }} columns={[
          { title: '角色名称', dataIndex: 'name' }, { title: '角色编码', dataIndex: 'code' },
          { title: '数据范围', render: (_, r) => <Tag className="scope-tag">{r.scopeState === 'UNCONFIGURED' ? '范围待配置' : scopeLabels[r.scope?.type ?? ''] ?? '未知范围'}</Tag> },
          { title: '状态', render: (_, r) => <Space><Tag>{r.enabled ? '启用' : '停用'}</Tag>{r.protected && <Tag color="gold">受保护</Tag>}</Space> },
          { title: '操作', render: (_, r) => canManage && !r.protected ? <Button aria-label={`管理角色 ${r.code}`} disabled={pending} onClick={() => void edit(r)}>管理</Button> : '只读' },
        ]} />}
    <Modal title="新建普通角色" open={creating} footer={null} closable={!pending} maskClosable={false} onCancel={() => { if (!pending) setCreating(false); }}>
      {writeAlert}<Form form={createForm} layout="vertical" disabled={pending} onFinish={v => void save('/api/v1/iam/roles', 'POST', { code: v.code, name: v.name })}>
        <Form.Item label="角色编码" name="code" rules={[{ required: true, whitespace: true, max: 64 }]}><Input maxLength={64} /></Form.Item>
        <Form.Item label="角色名称" name="name" rules={[{ required: true, whitespace: true, max: 128 }]}><Input maxLength={128} /></Form.Item>
        <Typography.Paragraph type="secondary">初始无功能权限，数据范围为仅本人，仓库范围为空。</Typography.Paragraph>
        <Button type="primary" htmlType="submit" loading={pending}>创建角色</Button>
      </Form>
    </Modal>
    <Drawer title={selected ? `管理角色 · ${selected.code}` : '管理角色'} open={!!selected} size={520} closable={!pending} maskClosable={false} onClose={() => { if (!pending) setSelected(undefined); }}>
      {writeAlert}{selected && <>
        <Form form={metadataForm} layout="vertical" disabled={pending} onFinish={v => void save(`/api/v1/iam/roles/${selected.id}`, 'PUT', { expectedVersion: selected.version, name: v.name, enabled: v.enabled })}>
          <Form.Item label="角色名称" name="name" rules={[{ required: true, whitespace: true, max: 128 }]}><Input maxLength={128} /></Form.Item>
          <Form.Item label="启用角色" name="enabled" valuePropName="checked"><Switch /></Form.Item>
          <Button htmlType="submit" loading={pending}>保存基本信息</Button>
        </Form>
        <Typography.Title level={4}>功能权限</Typography.Title>
        <Form form={permissionForm} layout="vertical" disabled={pending} onFinish={v => void save(`/api/v1/iam/roles/${selected.id}/permissions`, 'PUT', { expectedVersion: selected.version, permissions: v.permissions ?? [] })}>
          <Form.Item label="可用权限" name="permissions"><Select mode="multiple" optionFilterProp="label" options={catalog.map(p => ({ value: p.code, label: `${p.group} · ${p.label}` }))} /></Form.Item>
          <Button type="primary" htmlType="submit" loading={pending}>保存权限</Button>
        </Form>
        <Typography.Paragraph type="secondary" style={{ marginTop: 24 }}>功能权限与数据范围分别配置；受保护管理员角色不可在此维护。</Typography.Paragraph>
      </>}
    </Drawer>
  </section>;
}
