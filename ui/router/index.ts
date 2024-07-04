import {createRouter, createWebHistory, RouteRecordRaw} from 'vue-router'
import InstrumentComponent from '../components/instrument-component.vue'
import PortfolioTransactionComponent from '../components/portfolio-transaction-component.vue'
import PortfolioSummaryComponent from '../components/portfolio-summary-component.vue'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: 'Instruments',
    component: InstrumentComponent,
  },
  {
    path: '/transactions',
    name: 'Transactions',
    component: PortfolioTransactionComponent,
  },
  {
    path: '/summary',
    name: 'Portfolio Summary',
    component: PortfolioSummaryComponent,
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
