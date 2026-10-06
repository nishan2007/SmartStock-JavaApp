# SmartStock website

This is the editable React/TypeScript website source, moved from `storefront`
into the existing `website` directory. Run `pnpm install --frozen-lockfile`,
`pnpm build`, and `pnpm test` here.

`pnpm build` writes generated browser assets to `../src/storefront-web` so Maven
can package them inside the Java application. Do not edit that generated bundle.
The Java API stays in `../src/services/Storefront*`; Cloudflare routing stays in
`../cloudflare/smartstock-storefront`; database extensions stay in
`../database/storefront`. These follow the existing application layout.

Company identity comes from `company_info` (name, primary and secondary mottos,
company logo), with address, phone and email from the selected `locations` row,
matching Company Preferences. Snapshots carry those public fields to backup
servers. Company logo manifests stay server-side; the browser gets a same-origin
image endpoint. Only registered, active company-logo raster assets are eligible.
Missing images fall back to the saved company name. Contact details are plain
text, never arbitrary HTML or links supplied by preferences.

The light website theme uses `DeckersPalette.java`: orange `#FF5B00` as the main accent, supporting green `#3CFF00`, yellow
`#FFF200`, coral `#F04F45`, magenta `#F100FF`, and restrained purple `#7022A8`; slate text `#0F172A`, muted `#475569`, background `#F1F5F9`, border
`#CBD5E1`, and white surfaces. The desktop's per-workstation dark-mode choice is
not a public company preference.

`node test/preview-server.mjs` starts a loopback-only synthetic preview on port
5180. Its company preferences are deliberately labeled examples. It does not
read the running store database or send email. See `../docs/storefront-testing.md`.

The September 25 Windows test installer predates the company-branding changes;
build a new candidate before installation testing of these changes.
