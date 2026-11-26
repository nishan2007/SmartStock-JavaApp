# Overseas Orders main workspace

The selected design is option 2, with its companion order list, new quote and
Add item mockups. The four `reference-*.png` images are the selected targets.
These are illustrative records, not production exports.

## Native screens

- **Orders list:** search, status filter, New quote, and Open. Double-click or
  Enter also opens the selected row. Returning preserves the search and selection.
- **Order details:** Items, Payments and Activity tabs, payment summary, receipt
  printing, and selected-item screenshot/product links. Shipment, collection,
  quote editing, refund and cancellation remain available through the existing
  server-authorized workflows. Less frequent commands are in More actions.
- **Quote:** customer lookup/linking, optional delivery details and notes,
  item table, shipping and a live total. Save draft retains a draft; Issue quote
  saves and issues it before exposing payment actions.
- **Add/Edit item:** seller choices, description/options/link, quantity and GYD
  price, optional purchase cost, file or clipboard screenshot, and a live total.
  Returning to the quote retains the entered customer and other items.

The entry point uses `NavigationManager` and its normal visible-first loading
shell. The main menu is hidden while the workspace is active. The back control
returns to it; the close control checks unsaved changes. Network work stays off
the event thread, and writes remain on the existing authenticated LAN API.

Screenshots can be selected before the first save. The client saves the draft,
uploads screenshots against its real server ID, saves the returned references,
and only then issues the quote. Each mutation has a new request ID and the current
server revision. A partial failure retains the saved draft identity and the
pending local screenshot bytes for retry.

At narrower window widths, the summary moves beneath the form. Pages scroll
vertically instead of hiding the summary off the right edge. Deckers palette,
logo, existing native buttons and licensed icon font are reused. The existing
server permissions, collection balance rules and cash-drawer rules are unchanged.

## Reproduce the native previews

From the repository root in PowerShell:

```powershell
mvn -q -f SmartStock/pom.xml test-compile -DskipTests
java '-Dsun.java2d.uiScale=1' -cp 'SmartStock/target/test-classes;SmartStock/target/classes;SmartStock/target/dependency/*' ui.screens.OverseasOrdersPreview SmartStock/target/overseas-design
java '-Dsun.java2d.uiScale=1' -cp 'SmartStock/target/test-classes;SmartStock/target/classes;SmartStock/target/dependency/*' ui.screens.OverseasOrdersPreview SmartStock/target/overseas-design-small 1024 768
& SmartStock/docs/overseas-orders-design/compare-previews.ps1
```

The preview uses a deterministic mock transport and does not connect to a store,
save production records, or print. It applies the Windows system look and feel
and SmartStock's theme pass. The output content viewport is 1488 × 1014, matching
the mockups after excluding their 44-pixel simulated title bar.

To run the native interaction tests (the normal Maven gate is headless):

```powershell
mvn -q -f SmartStock/pom.xml '-Dtest=OverseasOrdersWorkspaceTest' '-Djava.awt.headless=false' test
```

No release was published and no installed/live store application was changed.
Real store payment, drawer and printer verification remain separate deployment
checks.
