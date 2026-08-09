import { type Page } from '@playwright/test'

export type RouteStub = (page: Page) => Promise<void>

export const apiRoute = (path: string): RegExp => new RegExp(`/api${path}(\\?|$)`)
