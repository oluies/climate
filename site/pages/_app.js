import { useEffect } from 'react'
import Script from 'next/script'
import { useRouter } from 'next/router'

import '../styles/globals.css'
import siteConfig from '../config/siteConfig.js'
import * as goatcounter from '../lib/goatcounter'


function MyApp({ Component, pageProps }) {
  // Count client-side navigations; the script below counts the first load.
  // The hook is called unconditionally because React requires a stable hook
  // order - the config check happens inside it instead.
  const router = useRouter()
  useEffect(() => {
    if (!siteConfig.goatcounter) return
    const handleRouteChange = (url) => {
      goatcounter.pageview(url)
    }
    router.events.on('routeChangeComplete', handleRouteChange)
    return () => {
      router.events.off('routeChangeComplete', handleRouteChange)
    }
  }, [router.events])

  return (
    <>
      {siteConfig.goatcounter &&
        <Script
          strategy="afterInteractive"
          src="//gc.zgo.at/count.js"
          data-goatcounter={siteConfig.goatcounter}
        />
        }
      <Script
        dangerouslySetInnerHTML={{
          __html: `
          (function(d,t) {
            var BASE_URL="https://app.chatwoot.com";
            var g=d.createElement(t),s=d.getElementsByTagName(t)[0];
            g.src=BASE_URL+"/packs/js/sdk.js";
            g.defer = true;
            g.async = true;
            s.parentNode.insertBefore(g,s);
            g.onload=function(){
              window.chatwootSDK.run({
                websiteToken: 'tc1GJE9wAmNSHGVUUa8gDLcd',
                baseUrl: BASE_URL
              })
            }
          })(document,"script");
          `
        }}
        />
      <Component {...pageProps} />
  </>
  )
}

export default MyApp
