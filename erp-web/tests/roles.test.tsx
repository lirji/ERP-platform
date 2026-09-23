import { render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { RolesPage } from '../src/features/iam/RolesPage';
import { ApiError } from '../src/api/client';
it('shows API role records, not a hardcoded business catalogue', async () => {
  const client = { get: vi.fn().mockResolvedValue([{ id: 21, code: 'test-reader', name: '接口返回的测试角色', data_scope_type: 'DEPT' }]) };
  render(<RolesPage client={client} permitted />);
  expect(await screen.findByText('接口返回的测试角色')).toBeVisible();
  expect(screen.getByText('本部门')).toBeVisible();
});
it('does not call role API without permission', () => {
  const client = { get: vi.fn() }; render(<RolesPage client={client} permitted={false} />);
  expect(screen.getByText('没有角色查询权限')).toBeVisible(); expect(client.get).not.toHaveBeenCalled();
});
it('renders forbidden separately from the empty state', async () => {
  render(<RolesPage client={{ get: vi.fn().mockRejectedValue(new ApiError(403, '没有访问此功能的权限')) }} permitted />);
  expect(await screen.findByText('没有访问此功能的权限')).toBeVisible();
  expect(screen.queryByText('当前租户暂无角色')).not.toBeInTheDocument();
});
it('renders an actual empty API result', async () => {
  render(<RolesPage client={{ get: vi.fn().mockResolvedValue([]) }} permitted />);
  await waitFor(() => expect(screen.getByRole('button', { name: '刷新角色' })).not.toHaveClass('ant-btn-loading'));
  expect(screen.getByText('当前租户暂无角色')).toBeVisible();
});
