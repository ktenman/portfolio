import { API_ENDPOINTS } from '../../constants/api'
import { apiRoute, type RouteStub } from './stub'

const BUILD_INFO = {
  hash: 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678',
  time: '2026-08-07T09:15:00Z',
}

export const stubBuildInfo: RouteStub = async page => {
  await page.route(apiRoute(API_ENDPOINTS.BUILD_INFO), route => route.fulfill({ json: BUILD_INFO }))
}
