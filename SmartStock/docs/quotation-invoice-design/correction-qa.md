# Approved layout correction — 24 November 2026

Result: corrected layout verified in offline native Swing renders. Installed-app acceptance remains outstanding.

The original third mockup is the reference: exec-5aea498a-dbe4-40f4-8d77-d24af8207929.png in the chat generated-images folder. The previous 23 November visual pass was rejected and is superseded by this correction.

The overview now has customer/document filters and a compact list beside the document. Create quotation groups customer and dates above the six-column item table, with item actions and a right-aligned total/save footer. Custom item groups item/variant, quantity/pricing, print/placement, instructions and production date. Invoice details group payment history and fulfillment with their original operations.

Evidence: target/document-studio-correction/renders/corrected-four-screens.png, document-list.png, quotation-editor-wide.png, quotation-editor-1280.png, quotation-editor-small.png, quotation-customer-picker.png, custom-item-wide.png, custom-item-small.png, invoice-fulfillment.png, stock-item-wide.png, payment-entry.png and delivery-entry.png. These are actual production Swing components painted offscreen with fictional data; they are not installed-app screenshots. The comparison board scales whole screenshots without retouching controls. Wide renders use 1440 by 850, smaller quotation renders use 1280 by 680 and 1024 by 680. Below 1120 wide, editor and preview use tabs. Customer popup overlays the table while open, as a native popup normally does.

Intentional adaptations: retain shared Deckers branding, native Swing controls and existing module navigation. Keep the actual Grid/Compact Letter quotation/invoice documents and existing delivery-note format, as requested, rather than substituting the illustrative mockup paper. Advanced pricing and print options remain available through secondary controls.

Visual inspection verified field grouping, visible item actions/footer at 1280 by 680, custom form labels and controls, invoice payment/delivery sections, selected rows, status badges, numeric totals and Letter preview proportions. Fixed a blank status-renderer child layout found during final comparison. No remaining blocking layout defect was observed in these captures; this does not establish pixel-identical rendering on other OS/DPI settings.

Validation: isolated full Maven suite passed 1645 tests, zero failures/errors, 210 skips. After the final status renderer change, focused validation passed 31 tests, zero failures/errors/skips, including five layout cases. Repository security check and git diff --check passed.

Not performed: installed application update, live store/API/database acceptance, real payment or delivery posting, physical printer output or hardware verification. No release was packaged, published or installed.