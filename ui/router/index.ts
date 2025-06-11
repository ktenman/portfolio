import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import InstrumentsView from '../components/instruments/instruments-view.vue'
import TransactionsView from '../components/transactions/transactions-view.vue'
import PortfolioSummary from '../components/portfolio-summary.vue'
import Calculator from '../components/calculator.vue'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: 'Portfolio Summary',
    component: PortfolioSummary,
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
    path: '/calculator',
    name: 'Calculator',
    component: Calculator,
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
