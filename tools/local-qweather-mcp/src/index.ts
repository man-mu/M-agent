import express, { type Request, type Response } from 'express';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { SSEServerTransport } from '@modelcontextprotocol/sdk/server/sse.js';
import { readConfig } from './config.js';
import { QWeatherClient } from './qweatherClient.js';
import {
  queryWeatherNow,
  weatherNowInputSchema,
  weatherToolErrorResponse,
  weatherToolResponse
} from './weatherTool.js';

export function createMcpServer(client: QWeatherClient): McpServer {
  const server = new McpServer({
    name: 'local-qweather-mcp',
    version: '0.1.0'
  });

  server.registerTool(
    'weather_now',
    {
      title: '实时天气查询',
      description: '查询指定城市、和风 Location ID 或 lon,lat 经纬度的实时天气',
      inputSchema: weatherNowInputSchema.shape
    },
    async (input) => {
      try {
        return weatherToolResponse(await queryWeatherNow(client, input));
      } catch (error) {
        return weatherToolErrorResponse(error);
      }
    }
  );

  return server;
}

export function createApp() {
  const config = readConfig();
  const client = new QWeatherClient(config);
  const app = express();
  const transports = new Map<string, SSEServerTransport>();

  app.use(express.json());

  app.get('/health', (_req: Request, res: Response) => {
    res.json({
      ok: true,
      service: 'local-qweather-mcp',
      keyConfigured: config.keyConfigured,
      apiHost: config.apiHost,
      port: config.port
    });
  });

  app.get('/sse', async (_req: Request, res: Response) => {
    const server = createMcpServer(client);
    const transport = new SSEServerTransport('/messages', res);
    transports.set(transport.sessionId, transport);
    res.on('close', () => {
      transports.delete(transport.sessionId);
      void server.close();
    });
    await server.connect(transport);
  });

  app.post('/messages', async (req: Request, res: Response) => {
    const sessionId = String(req.query.sessionId || '');
    const transport = transports.get(sessionId);
    if (!transport) {
      res.status(404).json({ error: 'MCP SSE session not found' });
      return;
    }
    await transport.handlePostMessage(req, res, req.body);
  });

  app.get('/debug/weather-now', async (req: Request, res: Response) => {
    try {
      const result = await queryWeatherNow(client, {
        location: String(req.query.location || ''),
        adm: optionalString(req.query.adm),
        lang: optionalString(req.query.lang) || 'zh',
        unit: unitString(req.query.unit)
      });
      res.json(result);
    } catch (error) {
      const message = error instanceof Error ? error.message : '天气查询失败，请稍后重试';
      res.status(400).json({ error: message });
    }
  });

  return { app, config };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  const { app, config } = createApp();
  app.listen(config.port, () => {
    console.log(`local-qweather-mcp listening on http://127.0.0.1:${config.port}`);
  });
}

function optionalString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : undefined;
}

function unitString(value: unknown): 'm' | 'i' {
  return value === 'i' ? 'i' : 'm';
}
