import { mount, MountingOptions, VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter, Router } from 'vue-router'
import router from '../router/index'

interface TestingOptions extends MountingOptions<any> {
  initialRoute?: string
}

export function renderWithProviders(
  component: any,
  options: TestingOptions = {}
): VueWrapper<any> & { router: Router; queryClient: QueryClient } {
  const { initialRoute = '/', ...mountOptions } = options

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

  const wrapper = mount(component, {
    ...mountOptions,
    global: {
      ...mountOptions.global,
      plugins: [
        [VueQueryPlugin, { queryClient }],
        testRouter,
        ...(mountOptions.global?.plugins || []),
      ],
    },
  })

  return Object.assign(wrapper, { router: testRouter, queryClient })
}
