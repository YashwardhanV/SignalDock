/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#101820",
        paper: "#F4F0E8",
        coral: "#FF6B4A",
        mint: "#55D6A5",
        line: "#D9D4CA"
      },
      boxShadow: {
        lift: "0 18px 55px rgba(16, 24, 32, 0.10)"
      }
    }
  },
  plugins: []
};

