// GoatCounter counts the first page load itself, from the script tag in _app.js.
// This is for client-side navigations, which never reload the page and would
// otherwise go uncounted. The caller has already checked the config, so the only
// guard needed here is that the script has actually loaded.
export const pageview = (url) => {
  if (!(window.goatcounter && window.goatcounter.count)) return
  // Deferred a tick so next/head has committed the new <title> first; without
  // this the recorded title can lag one page behind on a client-side route change.
  setTimeout(() => {
    window.goatcounter.count({ path: url, title: document.title })
  }, 0)
}
