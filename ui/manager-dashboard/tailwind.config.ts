import type { Config } from 'tailwindcss'

const config: Config = {
  content: [
    './index.html',
    './src/**/*.{html,js,jsx,ts,tsx}',
    './node_modules/@tremor/**/*.{js,ts,jsx,tsx}',
  ],
  darkMode: ['selector', '[data-theme="monitor"], [data-theme="high-contrast"]'],
  theme: {
    transparent: 'transparent',
    current: 'currentColor',
    extend: {
      colors: {
        tremor: {
          brand: {
            faint: 'var(--accent-subtle)',
            muted: 'var(--accent-secondary)',
            subtle: 'var(--accent-secondary)',
            DEFAULT: 'var(--accent)',
            emphasis: 'var(--accent-muted)',
            inverted: 'var(--text-primary)',
          },
          background: {
            muted: 'var(--bg-secondary)',
            subtle: 'var(--bg-tertiary)',
            DEFAULT: 'var(--bg-app)',
            emphasis: 'var(--bg-elevated)',
          },
          border: {
            DEFAULT: 'var(--border-color)',
          },
          ring: {
            DEFAULT: 'var(--accent)',
          },
          content: {
            subtle: 'var(--text-muted)',
            DEFAULT: 'var(--text-secondary)',
            emphasis: 'var(--text-secondary)',
            strong: 'var(--text-data)',
            inverted: 'var(--bg-app)',
          },
        },
        'dark-tremor': {
          brand: {
            faint: 'var(--accent-subtle)',
            muted: 'var(--accent-muted)',
            subtle: 'var(--accent-secondary)',
            DEFAULT: 'var(--accent)',
            emphasis: 'var(--accent-muted)',
            inverted: 'var(--text-primary)',
          },
          background: {
            muted: 'var(--bg-secondary)',
            subtle: 'var(--bg-tertiary)',
            DEFAULT: 'var(--bg-app)',
            emphasis: 'var(--bg-elevated)',
          },
          border: {
            DEFAULT: 'var(--border-color)',
          },
          ring: {
            DEFAULT: 'var(--accent)',
          },
          content: {
            subtle: 'var(--text-muted)',
            DEFAULT: 'var(--text-secondary)',
            emphasis: 'var(--text-secondary)',
            strong: 'var(--text-data)',
            inverted: 'var(--bg-app)',
          },
        },
      },
      boxShadow: {
        'tremor-input': '0 1px 2px 0 rgb(0 0 0 / 0.05)',
        'tremor-card': '0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)',
        'tremor-dropdown': '0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)',
        'dark-tremor-input': '0 1px 2px 0 rgb(0 0 0 / 0.25)',
        'dark-tremor-card': '0 1px 3px 0 rgb(0 0 0 / 0.35), 0 1px 2px -1px rgb(0 0 0 / 0.35)',
        'dark-tremor-dropdown': '0 4px 6px -1px rgb(0 0 0 / 0.35), 0 2px 4px -2px rgb(0 0 0 / 0.35)',
      },
      borderRadius: {
        'tremor-small': '0.375rem',
        'tremor-default': '0.5rem',
        'tremor-full': '9999px',
      },
      fontSize: {
        'tremor-label': ['0.75rem', { lineHeight: '1rem' }],
        'tremor-default': ['0.875rem', { lineHeight: '1.25rem' }],
        'tremor-title': ['1.125rem', { lineHeight: '1.75rem' }],
        'tremor-metric': ['1.875rem', { lineHeight: '2.25rem' }],
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'system-ui', '-apple-system', 'sans-serif'],
        display: ['Plus Jakarta Sans', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'Cascadia Code', 'Source Code Pro', 'monospace'],
      },
    },
  },
  safelist: [
    {
      pattern:
        /^(bg-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-(?:50|100|200|300|400|500|600|700|800|900|950))$/,
      variants: ['hover', 'ui-selected'],
    },
    {
      pattern:
        /^(text-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-(?:50|100|200|300|400|500|600|700|800|900|950))$/,
      variants: ['hover', 'ui-selected'],
    },
    {
      pattern:
        /^(border-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-(?:50|100|200|300|400|500|600|700|800|900|950))$/,
      variants: ['hover', 'ui-selected'],
    },
    {
      pattern:
        /^(ring-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-(?:50|100|200|300|400|500|600|700|800|900|950))$/,
      variants: ['hover', 'ui-selected'],
    },
    {
      pattern:
        /^(stroke-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-(?:50|100|200|300|400|500|600|700|800|900|950))$/,
      variants: ['hover', 'ui-selected'],
    },
    {
      pattern:
        /^(fill-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-(?:50|100|200|300|400|500|600|700|800|900|950))$/,
      variants: ['hover', 'ui-selected'],
    },
  ],
  plugins: [],
}

export default config
