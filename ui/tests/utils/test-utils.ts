import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import type { Component } from 'vue'

export function mountWithRouter(component: Component, options = {}) {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/', component: { template: '<div>Home</div>' } }],
  })

  return mount(component, {
    global: {
      plugins: [router],
    },
    ...options,
  })
}

export function createMockApiResponse<T>(data: T, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(data),
    text: () => Promise.resolve(JSON.stringify(data)),
    headers: new Headers(),
  } as Response)
}

export function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
