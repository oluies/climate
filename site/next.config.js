// The book owns the root of gh-pages; this site is published under /site by
// .github/workflows/main.yml, so asset and link URLs must be prefixed to match.
// That workflow is currently disabled; these settings keep the two consistent
// if it is ever switched back on.
const base = process.env.SITE_BASE_PATH || ''

module.exports = {
  basePath: base,
  assetPrefix: base ? base + '/' : undefined,
  trailingSlash: true,
  async redirects() {
    return [
      {
        // 2021-08-18 - because tweeted with this url today (can remove in ~6m?)
        // 2021-09-02 updated with new dest
        source: '/sewtha/README',
        destination: '/without-hot-air/',
        permanent: true,
      },
      {
        // 2021-09-02 moved sewtha to without-hot-air
        source: '/sewtha/',
        destination: '/without-hot-air/',
        permanent: true,
      },
    ]
  },
}
