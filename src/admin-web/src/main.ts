import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElLoading,
  ElPagination,
  ElResult,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import 'element-plus/dist/index.css'
import { createApp } from 'vue'

import App from './App.vue'
import { router } from './app/router'
import './styles/main.css'

const app = createApp(App)
for (const component of [
  ElAlert,
  ElButton,
  ElDatePicker,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElPagination,
  ElResult,
  ElTable,
  ElTableColumn,
  ElTag,
]) {
  app.use(component)
}
app.use(ElLoading).use(router).mount('#app')
