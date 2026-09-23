import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import { RolesPage } from '../src/features/iam/RolesPage';
import { ApiError } from '../src/api/client';
const role = { id: '21', version: '0', code: 'test-reader', name: '接口返回的测试角色', enabled: true, protected: false, permissions: [], scope: { type: 'DEPT' }, scopeState: 'VALID' };
const page = { list: [role], total: 1, page: 1, size: 20 };
it('shows API role records and real pagination', async () => {
  const client = { command: vi.fn(), get: vi.fn().mockResolvedValue(page) };
  render(<RolesPage client={client} permitted />);
  expect(await screen.findByText('接口返回的测试角色')).toBeVisible();
  expect(screen.getByText('本部门')).toBeVisible(); expect(screen.queryByText('新建角色')).not.toBeInTheDocument();
});
it('does not call role API without permission', () => {
  const client = { command: vi.fn(), get: vi.fn() }; render(<RolesPage client={client} permitted={false} />);
  expect(screen.getByText('没有角色查询权限')).toBeVisible(); expect(client.get).not.toHaveBeenCalled();
});
it('renders forbidden separately from the empty state', async () => {
  render(<RolesPage client={{ command: vi.fn(), get: vi.fn().mockRejectedValue(new ApiError(403, '没有访问此功能的权限')) }} permitted />);
  expect(await screen.findByText('没有访问此功能的权限')).toBeVisible(); expect(screen.queryByText('当前租户暂无角色')).not.toBeInTheDocument();
});
it('renders an actual empty API result', async () => {
  render(<RolesPage client={{ command: vi.fn(), get: vi.fn().mockResolvedValue({ ...page, list: [], total: 0 }) }} permitted />);
  await waitFor(() => expect(screen.getByRole('button', { name: '刷新角色' })).not.toHaveClass('ant-btn-loading'));
  expect(screen.getByText('当前租户暂无角色')).toBeVisible();
});
it('retains command key and form after unknown network result', async () => {
  const user = userEvent.setup();
  const client = { get: vi.fn().mockResolvedValue(page), command: vi.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValue({ ...role, code: 'BUYER', name: '采购员' }) };
  render(<RolesPage client={client} permitted canManage />);
  await user.click(await screen.findByText('新建角色'));
  await user.type(screen.getByLabelText('角色编码'), 'BUYER'); await user.type(screen.getByLabelText('角色名称'), '采购员');
  await user.click(screen.getByRole('button', { name: '创建角色' }));
  expect(await screen.findByText('请求未能完成，请核对连接后使用原操作重试')).toBeInTheDocument();
  expect(screen.getByLabelText('角色编码')).toHaveValue('BUYER');
  await user.click(screen.getByRole('button', { name: '创建角色' }));
  await waitFor(() => expect(client.command).toHaveBeenCalledTimes(2));
  expect(client.command.mock.calls[0][3]).toEqual(client.command.mock.calls[1][3]);
  expect(client.command.mock.calls[0][2]).toEqual({ code: 'BUYER', name: '采购员' });
});
it('never offers editing for protected roles', async () => {
  render(<RolesPage client={{ command: vi.fn(), get: vi.fn().mockResolvedValue({ ...page, list: [{ ...role, protected: true }] }) }} permitted canManage />);
  expect(await screen.findByText('受保护')).toBeVisible(); expect(screen.queryByRole('button', { name: /^管理角色/ })).not.toBeInTheDocument();
});
