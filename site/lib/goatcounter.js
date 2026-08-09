// GoatCounter counts the first page load itself, once count.js loads. This
// module handles client-side navigations, which never reload the page.
//
// The script is loaded afterInteractive, so a fast click through to a second
// route can fire routeChangeComplete before window.goatcounter exists. Rather
// than lose that pageview, buffer it and let the script's onLoad flush it.
let pending = null

const ready = () => typeof window !== 'undefined' && window.goatcounter && window.goatcounter.count

const send = (url) => {
  // Deferred a tick so next/head has committed the new <title>; without this
  // the recorded title can lag one page behind on a client-side route change.
  setTimeout(() => {
    window.goatcounter.count({ path: url, title: document.title })
  }, 0)
}

export const pageview = (url) => {
  if (ready()) send(url)
  else pending = url
}

export const flush = () => {
  if (pending && ready()) {
    send(pending)
    pending = null
  }
}
