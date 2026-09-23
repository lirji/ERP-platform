import { describe, expect, it, vi } from 'vitest';
import { createApiClient, type TokenSession } from '../src/api/client';
function session(): TokenSession { return { token: vi.fn().mockResolvedValue('test-only-token'), refresh: vi.fn().mockResolvedValue(undefined), clear: vi.fn().mockResolvedValue(undefined) }; }
describe('authenticated GET boundary', () => {
  it('renews a rejected token once and keeps the request on the same origin', async () => {
    const auth = session();
    const transport = vi.fn().mockResolvedValueOnce(new Response('{}', { status: 401 }))
      .mockResolvedValueOnce(new Response('{"ok":true}'));
    expect(await createApiClient(auth, transport).get('/api/v1/iam/me')).toEqual({ ok: true });
    expect(auth.refresh).toHaveBeenCalledTimes(1);
    await expect(createApiClient(auth, transport).get('https://outside.example/api')).rejects.toThrow('同源');
    expect(transport).toHaveBeenCalledTimes(2);
  });
  it('does not refresh a forbidden role request or turn it into empty data', async () => {
    const auth = session();
    const transport = vi.fn().mockResolvedValue(new Response('{"traceId":"trace-403"}', { status: 403 }));
    await expect(createApiClient(auth, transport).get('/api/v1/iam/roles')).rejects.toMatchObject({ status: 403, traceId: 'trace-403' });
    expect(auth.refresh).not.toHaveBeenCalled();
  });
  it('clears an invalid session after the second 401 instead of looping', async () => {
    const auth = session();
    const transport = vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { status: 401 })));
    await expect(createApiClient(auth, transport).get('/api/v1/iam/me')).rejects.toMatchObject({ status: 401 });
    expect(transport).toHaveBeenCalledTimes(2); expect(auth.clear).toHaveBeenCalledTimes(1);
  });
  it('coalesces concurrent refresh failures and exposes no token', async () => {
    const auth = session();
    let reject!: (error: Error) => void;
    auth.refresh = vi.fn(() => new Promise<void>((_, fail) => { reject = fail; }));
    const transport = vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { status: 401 })));
    const api = createApiClient(auth, transport);
    const results = Promise.allSettled([api.get('/api/v1/iam/me'), api.get('/api/v1/iam/roles')]);
    await vi.waitFor(() => expect(auth.refresh).toHaveBeenCalledTimes(1));
    reject(new Error('sensitive refresh detail'));
    for (const result of await results) {
      expect(result.status).toBe('rejected');
      if (result.status === 'rejected') expect(result.reason.message).not.toContain('sensitive');
    }
  });
});
it('retains the same idempotency key and payload across token refresh', async () => {
  const auth = session(); const transport = vi.fn().mockResolvedValueOnce(new Response('{}', { status: 401 })).mockResolvedValueOnce(new Response('{"id":"123"}', { status: 201 }));
  const api = createApiClient(auth, transport);
  await expect(api.command('/api/v1/iam/roles', 'POST', { code: 'BUYER', name: '采购员' }, 'operation-key')).resolves.toEqual({ id: '123' });
  for (const call of transport.mock.calls) {
    expect(call[1].headers['Idempotency-Key']).toBe('operation-key'); expect(call[1].body).toBe('{"code":"BUYER","name":"采购员"}');
  }
});
