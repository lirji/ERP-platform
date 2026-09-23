import { useEffect, useState } from 'react';
import { Alert, Button, Empty, Flex, Table, Tag, Typography } from 'antd';
import type { ApiClient } from '../../api/client';
import { ApiError } from '../../api/client';
interface Role { id: number; code: string; name: string; data_scope_type: string }
const scopeLabels: Record<string, string> = {
  SELF: '仅本人', DEPT: '本部门', DEPT_AND_BELOW: '本部门及下级',
  SPECIFIED_ORG: '指定组织', SPECIFIED_COMPANY: '指定公司', ALL: '本租户全部',
};
export function RolesPage({ client, permitted }: { client: ApiClient; permitted: boolean }) {
  const [rows, setRows] = useState<Role[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error>();
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!permitted) return;
    const controller = new AbortController();
    setLoading(true); setRows([]); setError(undefined);
    client.get<unknown>('/api/v1/iam/roles', controller.signal).then(data => {
      if (controller.signal.aborted) return;
      if (!Array.isArray(data) || !data.every(r => Number.isSafeInteger(r.id)
          && typeof r.code === 'string' && typeof r.name === 'string' && typeof r.data_scope_type === 'string'))
        throw new Error('角色响应格式不兼容，请联系管理员');
      setRows(data);
    }).catch((cause: unknown) => {
      if (!controller.signal.aborted) setError(cause instanceof ApiError ? cause : new Error('角色加载失败，请稍后重试'));
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [client, permitted, revision]);
  if (!permitted) return <Alert type="warning" showIcon title="没有角色查询权限" description="请联系管理员申请访问权限。" />;
  return <section aria-labelledby="roles-title">
    <Flex justify="space-between" align="center" gap={16}>
      <div><Typography.Title id="roles-title" level={2}>角色目录</Typography.Title>
        <Typography.Paragraph type="secondary">查看当前租户的角色与数据范围。</Typography.Paragraph></div>
      <Button aria-label="刷新角色" onClick={() => setRevision(v => v + 1)} loading={loading}>刷新角色</Button>
    </Flex>
    {error ? <Alert type="error" showIcon title={error.message}
      description={error instanceof ApiError && error.traceId ? `关联编号：${error.traceId}` : '请检查连接或重试。'}
      action={error instanceof ApiError && error.status === 401 ? <Button href="/login">重新登录</Button> : undefined} />
      : <Table<Role> rowKey="id" loading={loading} dataSource={rows} pagination={false} scroll={{ x: 680 }}
        locale={{ emptyText: <Empty description="当前租户暂无角色" /> }}
        columns={[
          { title: '角色名称', dataIndex: 'name', width: 240 },
          { title: '角色编码', dataIndex: 'code', width: 200 },
          { title: '数据范围', dataIndex: 'data_scope_type', render: (scope: string) => <Tag className="scope-tag">{scopeLabels[scope] ?? `未知范围（${scope}）`}</Tag> },
        ]} />}
  </section>;
}
