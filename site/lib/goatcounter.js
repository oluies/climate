// count.js is loaded with no_onload, so nothing is counted until start() runs.
// That makes the sequence deterministic: the landing path is whatever the page
// was opened at, captured before any client-side navigation can change it, and
// every later route change is counted as it happens.
const landing =
  typeof window !== 'undefined'
    ? window.location.pathname + window.location.search
    : null

let started = false
let pending = []

const ready = () =>
  typeof window !== 'undefined' && window.goatcounter && window.goatcounter.count

const send = (path) => {
  // Deferred a tick so next/head has committed the new <title>; without this
  // the recorded title can lag one page behind on a client-side route change.
  setTimeout(() => {
    window.goatcounter.count({ path, title: document.title })
  }, 0)
}

// Called from the script's onLoad: count the landing page, then anything that
// happened while the script was still downloading.
export const start = () => {
  if (started || !ready()) return
  started = true
  send(landing)
  pending.forEach(send)
  pending = []
}

export const pageview = (url) => {
  if (started) send(url)
  else pending.push(url)
}
