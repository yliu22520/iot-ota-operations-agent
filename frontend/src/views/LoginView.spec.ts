import { render } from '@testing-library/vue'
import ElementPlus from 'element-plus'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import LoginView from './LoginView.vue'

describe('LoginView', () => {
  it('keeps the local demo password out of the initial browser form', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/login', component: LoginView }] })
    await router.push('/login')

    render(LoginView, {
      global: {
        plugins: [createTestingPinia(), router, ElementPlus],
      },
    })

    const usernameInput = document.querySelector<HTMLInputElement>('input[autocomplete="username"]')
    const passwordInput = document.querySelector<HTMLInputElement>('input[autocomplete="current-password"]')

    expect(usernameInput?.value).toBe('demo-operator')
    expect(passwordInput?.value).toBe('')
  })
})
