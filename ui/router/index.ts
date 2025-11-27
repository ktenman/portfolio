import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import InstrumentsView from '../components/instruments/instruments-view.vue'
import TransactionsView from '../components/transactions/transactions-view.vue'
import PortfolioSummaryDto from '../components/portfolio-summary.vue'
import Calculator from '../components/calculator.vue'
import EtfBreakdown from '../components/etf/etf-breakdown.vue'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: 'Portfolio Summary',
    component: PortfolioSummaryDto,
  },
  {
    path: '/transactions',
    name: 'Transactions',
    component: TransactionsView,
  },
  {
    path: '/instruments',
    name: 'Instruments',
    component: InstrumentsView,
  },
  {
    path: '/etf-breakdown',
    name: 'ETF Breakdown',
    component: EtfBreakdown,
  },
  {
    path: '/calculator',
    name: 'Calculator',
    component: Calculator,
  },
  {
    path: '/portfolio-xirr',
    redirect: '/calculator',
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
