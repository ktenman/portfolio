import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router'
import InstrumentComponent from '../components/instrument-component.vue'
import PortfolioTransactionComponent from '../components/portfolio-transaction-component.vue'
import PortfolioSummaryComponent from '../components/portfolio-summary-component.vue'

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
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

export default router
