import siteConfig from '../config/siteConfig.js'

// GoatCounter counts the first page load itself, from the script tag in _app.js.
// This is for client-side navigations, which never reload the page and would
// otherwise go uncounted.
export const pageview = (url) => {
  if (siteConfig.goatcounter && window.goatcounter && window.goatcounter.count) {
    window.goatcounter.count({ path: url })
  }
}
