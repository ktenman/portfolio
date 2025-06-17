import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import InstrumentsView from '../components/instruments/instruments-view.vue'
import TransactionsView from '../components/transactions/transactions-view.vue'
import PortfolioSummary from '../components/portfolio-summary.vue'
import Calculator from '../components/calculator.vue'
import TailwindTest from '../components/shared/tailwind-test.vue'
import PortfolioChartTest from '../components/migration-test/portfolio-chart-test.vue'
import FormInputTest from '../components/migration-test/form-input-test.vue'
import ButtonStylesTest from '../components/migration-test/button-styles-test.vue'

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
    path: '/tailwind-test',
    name: 'Tailwind Test',
    component: TailwindTest,
  },
  {
    path: '/migration/portfolio-chart',
    name: 'Portfolio Chart Test',
    component: PortfolioChartTest,
  },
  {
    path: '/migration/form-input',
    name: 'Form Input Test',
    component: FormInputTest,
  },
  {
    path: '/migration/button-styles',
    name: 'Button Styles Test',
    component: ButtonStylesTest,
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
