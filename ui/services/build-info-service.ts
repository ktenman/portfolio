import { ApiClient } from './api-client.ts'

export interface BuildInfo {
  hash: string;
  time: string;
}

export class BuildInfoService {
  private readonly apiUrl = '/api/build-info'

  async getBuildInfo(): Promise<BuildInfo> {
    try {
      return await ApiClient.get<BuildInfo>(this.apiUrl)
    } catch (error) {
      console.error('Failed to fetch build info:', error)
      return {
        hash: 'unknown',
        time: 'unknown'
      }
    }
  }
}
