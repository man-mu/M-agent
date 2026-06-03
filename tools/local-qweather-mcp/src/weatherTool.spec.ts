import { describe, expect, it, vi } from 'vitest';
import { QWeatherClient } from './qweatherClient.js';
import type { LocalQWeatherConfig } from './config.js';
import { isDirectQWeatherLocation, queryWeatherNow, weatherToolErrorResponse } from './weatherTool.js';

const config: LocalQWeatherConfig = {
  apiKey: 'test-key',
  apiHost: 'https://api.example.test',
  port: 18090,
  requestTimeoutMs: 1000,
  keyConfigured: true
};

describe('weather tool', () => {
  it('looks up city before querying weather for city name', async () => {
    const client = new QWeatherClient(config);
    const lookup = vi.spyOn(client, 'lookupCity').mockResolvedValue({
      name: '北京',
      id: '101010100',
      adm1: '北京市',
      country: '中国'
    });
    const getNow = vi.spyOn(client, 'getNow').mockResolvedValue({
      code: '200',
      now: {
        obsTime: '2026-06-03T10:00+08:00',
        temp: '26',
        feelsLike: '27',
        text: '晴',
        windDir: '东北风',
        windScale: '2',
        humidity: '40'
      }
    });

    const result = await queryWeatherNow(client, { location: '北京', lang: 'zh', unit: 'm' });

    expect(lookup).toHaveBeenCalledWith('北京', { adm: undefined, lang: 'zh' });
    expect(getNow).toHaveBeenCalledWith('101010100', { lang: 'zh', unit: 'm' });
    expect(result.summary).toContain('北京');
    expect(result.summary).toContain('晴');
  });

  it('uses direct weather endpoint for location id or coordinates', () => {
    expect(isDirectQWeatherLocation('101010100')).toBe(true);
    expect(isDirectQWeatherLocation('116.41,39.92')).toBe(true);
    expect(isDirectQWeatherLocation('北京')).toBe(false);
  });

  it('returns user friendly MCP error response', () => {
    const response = weatherToolErrorResponse(new Error('缺少 QWEATHER_API_KEY'));

    expect(response.isError).toBe(true);
    expect(response.content[0].text).toContain('QWEATHER_API_KEY');
  });
});
