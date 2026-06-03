import { afterEach, describe, expect, it, vi } from 'vitest';
import { QWeatherClient, QWeatherError } from './qweatherClient.js';
import type { LocalQWeatherConfig } from './config.js';

const baseConfig: LocalQWeatherConfig = {
  apiKey: 'test-key',
  apiHost: 'https://api.example.test',
  port: 18090,
  requestTimeoutMs: 1000,
  keyConfigured: true
};

describe('QWeatherClient', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('sets X-QW-Api-Key header', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response({
      code: '200',
      location: [{ name: '北京', id: '101010100' }]
    }));
    const client = new QWeatherClient(baseConfig);

    await client.lookupCity('北京', { lang: 'zh' });

    expect(fetchMock).toHaveBeenCalledOnce();
    const [, init] = fetchMock.mock.calls[0];
    expect((init?.headers as Record<string, string>)['X-QW-Api-Key']).toBe('test-key');
  });

  it('returns clear error when key is missing', async () => {
    const client = new QWeatherClient({ ...baseConfig, apiKey: undefined, keyConfigured: false });

    await expect(client.getNow('101010100', { lang: 'zh', unit: 'm' }))
      .rejects.toThrow('缺少 QWEATHER_API_KEY');
  });

  it('converts QWeather API code to user friendly error', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(response({ code: '401' }));
    const client = new QWeatherClient(baseConfig);

    await expect(client.getNow('101010100', { lang: 'zh', unit: 'm' }))
      .rejects.toThrow('和风天气认证失败');
  });
});

function response(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: async () => body
  } as Response;
}
