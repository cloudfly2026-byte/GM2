// Type Imports
import type { VerticalMenuDataType } from '@/types/menuTypes'

const verticalMenuData = (): VerticalMenuDataType[] => [
  {
    label: 'Dashboard',
    href: '/home',
    icon: 'tabler-smart-home'
  },
  {
    label: 'Equipos Médicos',
    href: '/equipos',
    icon: 'tabler-stethoscope'
  },
  {
    label: 'Mantenimientos',
    href: '/mantenimientos',
    icon: 'tabler-tool'
  },
  {
    label: 'Calibraciones',
    href: '/calibraciones',
    icon: 'tabler-adjustments-alt'
  },
  {
    label: 'Instituciones',
    href: '/instituciones',
    icon: 'tabler-building-hospital'
  },
  {
    label: 'Reportes',
    href: '/reportes',
    icon: 'tabler-report'
  }
]

export default verticalMenuData
