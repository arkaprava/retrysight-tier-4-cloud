/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { createRoot } from 'react-dom/client'
import Root from './components/Root'
import './styles/tailwind.css'
import './styles/theme/tokens.css'
import './styles/global.css'

const el = document.getElementById('root')
if (!el) {
  throw new Error('Root element #root not found')
}

createRoot(el).render(<Root />)
