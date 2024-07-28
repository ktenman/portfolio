import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import InstrumentComponent from '../components/instrument-component.vue'
import PortfolioTransactionComponent from '../components/transaction-component.vue'
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
    component: PortfolioTransactionComponent,
  },
  {
    path: '/instruments',
    name: 'Instruments',
    component: InstrumentComponent,
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
