import { render, RenderOptions } from '@testing-library/vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter, Router } from 'vue-router'
import router from '../router/index'

interface TestingOptions extends RenderOptions<any> {
  initialRoute?: string
}

export function renderWithProviders(
  component: any,
  options: TestingOptions = {}
): ReturnType<typeof render> & { router: Router; queryClient: QueryClient } {
  const { initialRoute = '/', ...renderOptions } = options

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
        staleTime: 0,
      },
      mutations: {
        retry: false,
      },
    },
  })

  const testRouter = createRouter({
    history: createMemoryHistory(),
    routes: router.options.routes,
  })

  testRouter.push(initialRoute)

  return {
    ...render(component, {
      ...renderOptions,
      global: {
        ...renderOptions.global,
        plugins: [
          [VueQueryPlugin, { queryClient }],
          testRouter,
          ...(renderOptions.global?.plugins || []),
        ],
      },
    }),
    router: testRouter,
    queryClient,
  }
}
