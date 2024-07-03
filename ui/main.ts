import { createApp } from 'vue'
import App from './app.vue'
import './style.css'
import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap/dist/js/bootstrap.bundle.min.js'
import router from './router'

createApp(App).use(router).mount('#app')
