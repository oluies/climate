// count.js is loaded with no_onload, so nothing is counted until start() runs
// and the page is actually visible. That makes the sequence deterministic: the
// landing path is whatever the page was opened at, captured before any
// client-side navigation can change it, and it is always counted first.
const landing =
  typeof window !== 'undefined'
    ? window.location.pathname + window.location.search
    : null

// no_onload also disables count.js's own hidden-page deferral, so a page opened
// in a background tab or prerendered would otherwise register a view the moment
// the script loads. Defer to first visibility instead.
const whenVisible = (fn) => {
  if (typeof document === 'undefined') return
  if (document.visibilityState === 'visible') return fn()
  const once = () => {
    if (document.visibilityState !== 'visible') return
    document.removeEventListener('visibilitychange', once)
    fn()
  }
  document.addEventListener('visibilitychange', once)
}

const MAX_PENDING = 20
// Two flags, not one. `started` says the script has loaded; `flushed` says the
// landing view has actually been sent. A route change arriving between the two
// - on a hidden page, or before the deferral fires - must be buffered, or it
// would be counted on a hidden page and ahead of the landing page.
let started = false
let flushed = false
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

// Called from the script's onLoad. If onLoad never fires — blocked CDN, SRI
// mismatch, content blocker — nothing is counted, which is the intended
// failure mode: better to lose the data than to guess at it.
export const start = () => {
  if (started || !ready()) return
  started = true
  whenVisible(() => {
    send(landing)
    pending.forEach(send)
    pending = []
    flushed = true
  })
}

export const pageview = (url) => {
  if (flushed) send(url)
  // Bounded: a long SPA session must not accumulate an unbounded buffer if the
  // script never loads or the page is never brought to the front.
  else if (pending.length < MAX_PENDING) pending.push(url)
}
