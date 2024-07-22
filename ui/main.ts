import {createApp} from 'vue'
import App from './app.vue'
import './style.css'
import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap/dist/js/bootstrap.bundle.min.js'
import router from './router'

import {library} from '@fortawesome/fontawesome-svg-core'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'
import {faPencilAlt} from '@fortawesome/free-solid-svg-icons'
import {faTrashAlt} from '@fortawesome/free-solid-svg-icons/faTrashAlt'

library.add(faPencilAlt)
library.add(faTrashAlt)

const app = createApp(App)

app.component('FontAwesomeIcon', FontAwesomeIcon)
app.use(router).mount('#app')
