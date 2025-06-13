import { createApp } from 'vue'
import { VueQueryPlugin } from '@tanstack/vue-query'
import Toast from 'vue-toastification'
import 'vue-toastification/dist/index.css'
import App from './app.vue'
import './styles/main.scss'
import 'bootstrap/js/dist/modal'
import router from './router/index'

const app = createApp(App)

app.use(VueQueryPlugin)
app.use(Toast, {
  transition: 'Vue-Toastification__bounce',
  maxToasts: 20,
  newestOnTop: true,
})
app.use(router).mount('#app')
