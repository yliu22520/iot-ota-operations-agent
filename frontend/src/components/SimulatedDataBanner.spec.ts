import { render, screen } from '@testing-library/vue'
import ElementPlus from 'element-plus'
import SimulatedDataBanner from './SimulatedDataBanner.vue'

describe('SimulatedDataBanner', () => {
  it('明确标识当前展示的是模拟数据、诊断和重试', () => {
    render(SimulatedDataBanner, { global: { plugins: [ElementPlus] } })

    expect(screen.getByText(/模拟演示数据、诊断和重试/)).toBeTruthy()
    expect(screen.getByText(/不代表真实设备或生产平台状态/)).toBeTruthy()
  })
})
