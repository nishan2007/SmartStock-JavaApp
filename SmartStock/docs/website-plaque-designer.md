# Plaque designer

Plaques use the existing active custom-order item and active variant records.
Storefront snapshots attach `variants` to plaque items in `laserProducts` and
`sublimationProducts`. Each variant includes its own saved size, template width,
height, unit, and photo presence. Template dimensions are the artwork area, never
the overall physical size. Missing template dimensions disable editing.

The browser keeps a separate draft for each store/item/variant. It supports text,
fonts shared with the shirt designer, image uploads, physical-inch positioning,
rotation, dragging, transparent PNG artwork export, and editable JSON download
and restore. Uploads remain in the browser. This is a preview workflow while the
website is browse-only; it does not create orders or authorize production.

The nine named shapes in `website/src/plaqueGeometry.mjs` were traced from the
current variant photographs: diamond, tall star, blue flame with a gold star,
irregular pentagon, heart, pointed top, curved top, rounded pentagon, and curved
shield. The variant gallery shows each active plaque photo and opens its editor on click. Clicking the model or reference photo also returns to editing. Three.js uses the detailed photo-space silhouettes in `plaquePhotoModels.ts` and projects the actual item photograph onto the front surfaces, preserving faceting, inserts, ornamentation, and bases. The simpler procedural shapes are a fallback when a photo is unavailable. Depth, unseen surfaces, and centered
artwork placement are approximate because the item data has no measured depth
or placement offsets. Unknown shapes keep the editor but disable 3D rather than
substituting an unrelated model. The actual variant photograph remains available. Photo models preserve the photographed proportions, with bounded rotation because the rear surfaces cannot be reconstructed from a single front photo.
These are visualization models, not manufacturing/cutting files.

Photos are served through `/shop/image?kind=plaque&storeId=...&id=...&variantId=...`.
Both item and variant must occur in the published snapshot. Private asset
manifests remain server-side. Backup image prefetch includes variant photos.
No new schema, credentials, public storage policy, or migration is needed.

Deployment requires the server changes and frontend bundle together. The server
must be updated through the normal verified Windows release/install workflow;
source work and browser previews do not change the installed POS-SERVER app.
Publish the updated frontend only after the server exports variant metadata.
Existing browse-only and customer-data restrictions must remain enabled.

Validation: `node --test SmartStock/website/test/*.test.mjs`, TypeScript, Maven
`PlaqueCatalogTest,StorefrontImagePrefetchTest`, and
`node SmartStock/website/test/plaque-browser.mjs`. The browser check uses a local
Vite server and synthetic API responses and renders all nine 3D shapes, checks
rotated template bounds, draft isolation, and mobile containment. Real print
placement and manufactured plaque fit need staff verification before production.
