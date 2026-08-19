import { mount, VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import router from '../router/index'

export function renderWithProviders(component: any): VueWrapper<any> & {
  queryClient: QueryClient
} {
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

  testRouter.push('/')

  const wrapper = mount(component, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient }], testRouter],
    },
  })

  return Object.assign(wrapper, { queryClient })
}
