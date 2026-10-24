import { createApp } from 'vue'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './app.vue'
import '@fontsource-variable/geist/wght.css'
import '@fontsource-variable/geist-mono/wght.css'
import './styles/theme.css'
import router from './router/index'
import { forgetReload, reloadOnce } from './utils/dom'

window.addEventListener('vite:preloadError', reloadOnce)

const app = createApp(App)

app.use(VueQueryPlugin)
app.use(router)

router.isReady().then(() => {
  forgetReload()
  app.mount('#app')
})
