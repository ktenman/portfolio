import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import PortfolioSummaryDto from '../components/portfolio-summary.vue'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: 'Portfolio Summary',
    component: PortfolioSummaryDto,
  },
  {
    path: '/transactions',
    name: 'Transactions',
    component: () => import('../components/transactions/transactions-view.vue'),
  },
  {
    path: '/instruments',
    name: 'Instruments',
    component: () => import('../components/instruments/instruments-view.vue'),
  },
  {
    path: '/etf-breakdown',
    name: 'ETF Breakdown',
    component: () => import('../components/etf/etf-breakdown.vue'),
  },
  {
    path: '/diversification',
    name: 'Diversification',
    component: () => import('../components/diversification/diversification-calculator.vue'),
  },
  {
    path: '/calculator',
    name: 'Calculator',
    component: () => import('../components/calculator.vue'),
  },
  {
    path: '/monitoring',
    name: 'Monitoring',
    component: () => import('../components/monitoring/monitoring-view.vue'),
  },
  {
    path: '/radio',
    component: () => import('../components/radio/radio-view.vue'),
    children: [
      {
        path: '',
        name: 'Radio',
        component: () => import('../components/radio/radio-recordings.vue'),
      },
      {
        path: 'catch-up',
        name: 'RadioCatchUp',
        component: () => import('../components/radio/radio-catch-up.vue'),
      },
      {
        path: 'jarelkuulamine',
        redirect: '/radio/catch-up',
      },
      {
        path: ':name',
        component: () => import('../components/radio/radio-episode.vue'),
      },
      {
        path: ':name/:view(text|cuts)',
        redirect: to => `/radio/${String(to.params.name)}`,
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
