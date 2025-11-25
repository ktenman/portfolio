import { createApp } from 'vue'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './app.vue'
import './styles/main.scss'
import 'bootstrap/js/dist/modal'
import router from './router/index'

const app = createApp(App)

app.use(VueQueryPlugin)
app.use(router)

router.isReady().then(() => app.mount('#app'))
