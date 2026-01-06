export function getTheme() {
    if (typeof window === 'undefined') return 'light'

    const stored = localStorage.getItem('cena-theme')
    if (stored) return stored

    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
        return 'dark'
    }

    return 'light'
}

export function setTheme(theme) {
    if (typeof window === 'undefined') return

    localStorage.setItem('cena-theme', theme)
    document.documentElement.setAttribute('data-theme', theme)
}

export function toggleTheme() {
    const current = getTheme()
    const next = current === 'light' ? 'dark' : 'light'
    setTheme(next)
    return next
}

export function initTheme() {
    const theme = getTheme()
    setTheme(theme)
    return theme
}
