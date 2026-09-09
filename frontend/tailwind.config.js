/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: '#FF6B35',
        primaryHover: '#E55A2A',
        navy: '#1E293B',
        navyDark: '#0F172A',
        grayLight: '#F8FAFC',
        grayBorder: '#E2E8F0',
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
