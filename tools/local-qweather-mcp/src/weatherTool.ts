import { z } from 'zod';
import type { GeoLocation, QWeatherClient, WeatherNowResponse } from './qweatherClient.js';
import { QWeatherError } from './qweatherClient.js';

export const weatherNowInputSchema = z.object({
  location: z.string().min(1).describe('城市名、和风 Location ID 或 lon,lat 经纬度'),
  adm: z.string().min(1).optional().describe('上级行政区，用于城市重名消歧'),
  lang: z.string().min(1).default('zh').describe('返回语言，默认 zh'),
  unit: z.enum(['m', 'i']).default('m').describe('单位，m 为公制，i 为英制')
});

export type WeatherNowInput = z.infer<typeof weatherNowInputSchema>;

export interface WeatherNowToolResult {
  summary: string;
  query: {
    originalLocation: string;
    resolvedLocationId: string;
    resolvedLocationName?: string;
    adm?: string;
    directLocation: boolean;
  };
  weather: WeatherNowResponse;
}

export async function queryWeatherNow(client: QWeatherClient, input: WeatherNowInput): Promise<WeatherNowToolResult> {
  const parsed = weatherNowInputSchema.parse(input);
  const directLocation = isDirectQWeatherLocation(parsed.location);
  let resolved: GeoLocation | undefined;
  let locationId = parsed.location;

  if (!directLocation) {
    resolved = await client.lookupCity(parsed.location, {
      adm: parsed.adm,
      lang: parsed.lang
    });
    locationId = resolved.id;
  }

  const weather = await client.getNow(locationId, {
    lang: parsed.lang,
    unit: parsed.unit
  });

  return {
    summary: formatWeatherSummary(parsed.location, resolved, weather),
    query: {
      originalLocation: parsed.location,
      resolvedLocationId: locationId,
      resolvedLocationName: resolved?.name,
      adm: resolved ? [resolved.country, resolved.adm1, resolved.adm2].filter(Boolean).join(' / ') : parsed.adm,
      directLocation
    },
    weather
  };
}

export function weatherToolResponse(result: WeatherNowToolResult) {
  return {
    content: [
      {
        type: 'text' as const,
        text: result.summary
      },
      {
        type: 'text' as const,
        text: JSON.stringify(result, null, 2)
      }
    ]
  };
}

export function weatherToolErrorResponse(error: unknown) {
  const message = error instanceof QWeatherError || error instanceof Error
    ? error.message
    : '天气查询失败，请稍后重试';
  return {
    isError: true,
    content: [
      {
        type: 'text' as const,
        text: message
      }
    ]
  };
}

export function isDirectQWeatherLocation(location: string): boolean {
  const trimmed = location.trim();
  return /^\d{6,12}$/.test(trimmed)
    || /^-?\d+(\.\d+)?\s*,\s*-?\d+(\.\d+)?$/.test(trimmed);
}

function formatWeatherSummary(originalLocation: string, resolved: GeoLocation | undefined,
  response: WeatherNowResponse): string {
  const now = response.now;
  if (!now) {
    return `${originalLocation} 暂无实时天气数据。`;
  }
  const place = resolved
    ? [resolved.name, resolved.adm2, resolved.adm1, resolved.country].filter(Boolean).join('，')
    : originalLocation;
  return `${place}当前${now.text}，温度 ${now.temp}°，体感 ${now.feelsLike}°，湿度 ${now.humidity}%` +
    `，${now.windDir}${now.windScale}级，观测时间 ${now.obsTime}。`;
}
