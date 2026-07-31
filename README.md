This is a work-in-progress 2026 revised edition of David MacKay’s Sustainable Energy — Without the Hot Air (2008), rebuilt with Quarto from the markdown source preserved by the Life Itself climate project.

MacKay’s text and his own figures are reproduced unchanged. New material added in this revision is marked in the chapter where it appears; the first such chapter is The value of renewable energy as it scales in Part II.

The original book is © 2008 David J. C. MacKay and licensed CC BY-NC-SA 2.0 UK. This edition carries the same licence. Figures that carry third-party rights — the Private Eye cartoons, named-photographer photos, and the Ordnance Survey Crown Copyright maps — are omitted here and marked in place, because MacKay’s licence did not extend to them.

## Layout

```
content/          # main content folder - files in here are published
   notes/         # random notes zettelkasten style, published largely as blog
sewtha/           # special folder for sustainable energy without the hot air
site/             # website application (based on next.js)
```

## Developers

The website is built in Next.JS using tailwind and MDX.

### Local

1. `git clone`
2. `yarn install`
3. `yarn dev`

### Deployment

We are deploying to github pages using Next.JS static build run by github actions.

## Other work

Other work we have done:

* With Tommaso Venturini of KCL / Sciences Po we (Rufus Pollock)  built the [COP21 Treaty Texts website][cop21] in November / December 2015
* With Tommaso Venturini of KCL / Sciences Po we worked on [analyzing climate negotations][climate-talks]

[cop21]: http://cop21.okfnlabs.org/
[climate-talks]: https://github.com/rgrp/climate-negotiations
