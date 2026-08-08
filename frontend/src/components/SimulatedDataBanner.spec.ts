import { render, screen } from '@testing-library/vue'
import ElementPlus from 'element-plus'
import SimulatedDataBanner from './SimulatedDataBanner.vue'

describe('SimulatedDataBanner', () => {
  it('明确标识当前展示来自模拟器', () => {
    render(SimulatedDataBanner, { global: { plugins: [ElementPlus] } })

    expect(screen.getByText(/模拟器数据/)).toBeTruthy()
    expect(screen.getByText(/不代表真实生产设备/)).toBeTruthy()
  })
})
