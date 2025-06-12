import { createApp } from 'vue'
import App from './app.vue'
import './styles/main.scss'
import 'bootstrap/dist/js/bootstrap.bundle.min.js'
import router from './router'

import { library } from '@fortawesome/fontawesome-svg-core'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import { faPencilAlt, faTrashAlt } from '@fortawesome/free-solid-svg-icons'
import { IconDefinition } from '@fortawesome/fontawesome-svg-core'

library.add(faPencilAlt as IconDefinition)
library.add(faTrashAlt as IconDefinition)

const app = createApp(App)

app.component('FontAwesomeIcon', FontAwesomeIcon)
app.use(router).mount('#app')
