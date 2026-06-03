import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { describe, expect, it } from 'vitest';
import { DEFAULT_QWEATHER_API_HOST, readConfig } from './config.js';

describe('readConfig', () => {
  it('does not require a key to start', () => {
    const config = readConfig({}, mkdtempSync(join(tmpdir(), 'qweather-config-')));

    expect(config.keyConfigured).toBe(false);
    expect(config.apiKey).toBeUndefined();
    expect(config.apiHost).toBe(DEFAULT_QWEATHER_API_HOST);
    expect(config.port).toBe(18090);
  });

  it('reads local MCP keys without printing key values', () => {
    const root = mkdtempSync(join(tmpdir(), 'qweather-config-'));
    mkdirSync(join(root, '.local'));
    writeFileSync(join(root, '.local', 'mcp-keys.json'), JSON.stringify({
      QWEATHER_API_KEY: 'local-qweather-key',
      QWEATHER_API_HOST: 'https://example.qweather.test/'
    }));

    const config = readConfig({}, root);

    expect(config.keyConfigured).toBe(true);
    expect(config.apiKey).toBe('local-qweather-key');
    expect(config.apiHost).toBe('https://example.qweather.test');
  });

  it('environment variables override local key file', () => {
    const root = mkdtempSync(join(tmpdir(), 'qweather-config-'));
    mkdirSync(join(root, '.local'));
    writeFileSync(join(root, '.local', 'mcp-keys.json'), JSON.stringify({
      QWEATHER_API_KEY: 'local-qweather-key'
    }));

    const config = readConfig({ QWEATHER_API_KEY: 'env-qweather-key' }, root);

    expect(config.apiKey).toBe('env-qweather-key');
  });

  it('normalizes host without protocol to https url', () => {
    const config = readConfig({
      QWEATHER_API_HOST: 'example.qweather.test/',
      QWEATHER_API_KEY: 'env-qweather-key'
    }, mkdtempSync(join(tmpdir(), 'qweather-config-')));

    expect(config.apiHost).toBe('https://example.qweather.test');
  });
});
