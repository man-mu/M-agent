import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

export interface LocalQWeatherConfig {
  apiKey?: string;
  apiHost: string;
  port: number;
  requestTimeoutMs: number;
  keyConfigured: boolean;
}

export const DEFAULT_QWEATHER_API_HOST = 'https://api.qweather.com';
export const DEFAULT_LOCAL_QWEATHER_MCP_PORT = 18090;
export const DEFAULT_REQUEST_TIMEOUT_MS = 15000;

type LocalKeyFile = Record<string, unknown>;

export function loadLocalKeyFile(cwd = process.cwd()): Record<string, string> {
  const candidates = [
    resolve(cwd, '.local', 'mcp-keys.json'),
    resolve(cwd, '..', '..', '.local', 'mcp-keys.json')
  ];

  for (const path of candidates) {
    if (!existsSync(path)) {
      continue;
    }
    try {
      const parsed = JSON.parse(readFileSync(path, 'utf8')) as LocalKeyFile;
      return Object.fromEntries(
        Object.entries(parsed).filter((entry): entry is [string, string] => {
          const [, value] = entry;
          return typeof value === 'string' && value.trim().length > 0;
        })
      );
    } catch {
      return {};
    }
  }
  return {};
}

export function readConfig(env: NodeJS.ProcessEnv = process.env, cwd = process.cwd()): LocalQWeatherConfig {
  const localKeys = loadLocalKeyFile(cwd);
  const apiKey = firstPresent(env.QWEATHER_API_KEY, localKeys.QWEATHER_API_KEY);
  const apiHost = normalizeHost(firstPresent(env.QWEATHER_API_HOST, localKeys.QWEATHER_API_HOST)
    || DEFAULT_QWEATHER_API_HOST);
  const port = parsePositiveInteger(firstPresent(env.LOCAL_QWEATHER_MCP_PORT, localKeys.LOCAL_QWEATHER_MCP_PORT),
    DEFAULT_LOCAL_QWEATHER_MCP_PORT);
  const requestTimeoutMs = parsePositiveInteger(env.QWEATHER_REQUEST_TIMEOUT_MS,
    DEFAULT_REQUEST_TIMEOUT_MS);

  return {
    apiKey,
    apiHost,
    port,
    requestTimeoutMs,
    keyConfigured: Boolean(apiKey)
  };
}

function firstPresent(...values: Array<string | undefined>): string | undefined {
  return values.find((value) => value !== undefined && value.trim().length > 0)?.trim();
}

function normalizeHost(host: string): string {
  const trimmed = host.trim().replace(/\/+$/, '');
  return /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
}

function parsePositiveInteger(value: string | undefined, fallback: number): number {
  if (!value) {
    return fallback;
  }
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}
