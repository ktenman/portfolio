import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import InstrumentsView from '../components/instruments/instruments-view.vue'
import TransactionsView from '../components/transactions/transactions-view.vue'
import PortfolioSummaryComponent from '../components/portfolio-summary-component.vue'
import CalculatorComponent from '../components/calculator-component.vue'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: 'Portfolio Summary',
    component: PortfolioSummaryComponent,
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
    component: CalculatorComponent,
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
