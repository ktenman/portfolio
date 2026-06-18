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
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
