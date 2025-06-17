import type { Config } from 'tailwindcss'

export default {
  content: [
    './ui/**/*.{vue,js,ts,jsx,tsx}',
    './ui/index.html',
    './ui/utils/**/*.{js,ts}',
  ],
  theme: {
    extend: {
      screens: {
        'xs': '576px',
        'sm': '666px',
        'md': '768px',
        'md-lg': '794px',
        'lg': '992px',
        'xl': '1200px',
      },
      colors: {
        primary: '#007bff',
        secondary: '#6c757d',
        success: '#28a745',
        danger: '#dc3545',
        warning: '#ffc107',
        info: '#17a2b8',
        light: '#f8f9fa',
        dark: '#343a40',
      },
      spacing: {
        '0.25': '0.25rem',
        '0.5': '0.5rem',
        '1.5': '1.5rem',
        '2.5': '2.5rem',
        '3.5': '3.5rem',
        '4.5': '4.5rem',
      },
    },
  },
  plugins: [],
} satisfies Config