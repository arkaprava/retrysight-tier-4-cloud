/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { ApolloProvider } from '@apollo/client'
import { BrowserRouter } from 'react-router-dom'
import App from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { apolloClient } from '../graphql/client'
import { ThemeProvider } from '../theme/ThemeContext'

export default function Root() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <ApolloProvider client={apolloClient}>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </ApolloProvider>
      </AuthProvider>
    </ThemeProvider>
  )
}
