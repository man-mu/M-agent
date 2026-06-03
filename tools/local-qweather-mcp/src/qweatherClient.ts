import type { LocalQWeatherConfig } from './config.js';

export interface GeoLocation {
  name: string;
  id: string;
  lat?: string;
  lon?: string;
  adm1?: string;
  adm2?: string;
  country?: string;
}

export interface WeatherNow {
  obsTime: string;
  temp: string;
  feelsLike: string;
  icon?: string;
  text: string;
  wind360?: string;
  windDir: string;
  windScale: string;
  windSpeed?: string;
  humidity: string;
  precip?: string;
  pressure?: string;
  vis?: string;
  cloud?: string;
  dew?: string;
}

export interface WeatherNowResponse {
  code: string;
  updateTime?: string;
  fxLink?: string;
  now?: WeatherNow;
  refer?: unknown;
}

export interface CityLookupResponse {
  code: string;
  location?: GeoLocation[];
  refer?: unknown;
}

export class QWeatherError extends Error {
  constructor(
    message: string,
    public readonly code?: string
  ) {
    super(message);
    this.name = 'QWeatherError';
  }
}

export class QWeatherClient {
  constructor(private readonly config: LocalQWeatherConfig) {}

  async lookupCity(location: string, options: { adm?: string; lang: string }): Promise<GeoLocation> {
    const params = new URLSearchParams({
      location,
      lang: options.lang
    });
    if (options.adm) {
      params.set('adm', options.adm);
    }

    const data = await this.request<CityLookupResponse>('/geo/v2/city/lookup', params);
    if (!data.location?.length) {
      throw new QWeatherError('未找到城市，请补充行政区或经纬度', data.code);
    }
    return data.location[0];
  }

  async getNow(location: string, options: { lang: string; unit: string }): Promise<WeatherNowResponse> {
    const params = new URLSearchParams({
      location,
      lang: options.lang,
      unit: options.unit
    });
    const data = await this.request<WeatherNowResponse>('/v7/weather/now', params);
    if (!data.now) {
      throw new QWeatherError('和风天气未返回实时天气数据', data.code);
    }
    return data;
  }

  private async request<T extends { code?: string }>(path: string, params: URLSearchParams): Promise<T> {
    if (!this.config.apiKey) {
      throw new QWeatherError('缺少 QWEATHER_API_KEY，请在环境变量或 .local/mcp-keys.json 中配置和风天气 Key');
    }

    const url = `${this.config.apiHost}${path}?${params.toString()}`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.config.requestTimeoutMs);
    try {
      const response = await fetch(url, {
        headers: {
          'X-QW-Api-Key': this.config.apiKey,
          'User-Agent': 'local-qweather-mcp/0.1.0'
        },
        signal: controller.signal
      });

      if (!response.ok) {
        throw new QWeatherError(`和风天气 API HTTP ${response.status}`);
      }

      const data = await response.json() as T;
      if (data.code !== '200') {
        throw new QWeatherError(messageForQWeatherCode(data.code), data.code);
      }
      return data;
    } catch (error) {
      if (error instanceof QWeatherError) {
        throw error;
      }
      if (error instanceof Error && error.name === 'AbortError') {
        throw new QWeatherError('和风天气 API 请求超时，请稍后重试');
      }
      throw new QWeatherError('和风天气 API 请求失败，请检查网络或 API Host');
    } finally {
      clearTimeout(timeout);
    }
  }
}

export function messageForQWeatherCode(code?: string): string {
  switch (code) {
    case '204':
      return '未找到城市，请补充行政区或经纬度';
    case '400':
      return '和风天气请求参数不正确';
    case '401':
      return '和风天气认证失败，请检查 QWEATHER_API_KEY';
    case '402':
      return '和风天气调用额度不足或订阅不可用';
    case '403':
      return '和风天气访问被拒绝，请检查 Key 权限或 API Host';
    case '404':
      return '和风天气接口路径不存在';
    case '429':
      return '和风天气调用过于频繁，请稍后重试';
    case '500':
      return '和风天气服务暂时异常';
    default:
      return `和风天气 API 返回异常 code=${code || 'unknown'}`;
  }
}
