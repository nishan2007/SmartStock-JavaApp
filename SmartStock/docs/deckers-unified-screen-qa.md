# Deckers unified-screen acceptance — 2026-11-20

All 14 app templates/controllers mount in one document with the Deckers header,
Home and Account controls. No iframe, external-page tile navigation, or separate
screen login is used by the public Deckers workspace.

Verified with local fixture data in the browser: Orders, Inventory, Customer
Accounts, Phone Minutes, Balance Sheet, Employee History, Overseas Orders,
Scheduler, Add Item, Edit Items, Receiving Inventory, Image Studio, Order Design
(including order-number lookup and retained Save/Undo/Export/Send toolbar), and
Custom Order Spoils. Orders, Inventory and Customer Accounts were switched using
Home after one sign-in. The workspace URL remained unchanged. Off-store Receiving
showed the store-network sheet.

The fixture bypasses real network presence only in its explicit `store` scenario;
production proof enforcement was exercised independently through actual Java HTTPS
handlers and disposable PostgreSQL. It opened all 14 screen descriptors using the
same employee session, reached stock/Scheduler services, rejected missing CSRF,
cross-app requests, disabled device grants, absent LAN proof and proof replay.
No production credentials or business transactions were used in browser tests.

`DeckersScreensTest` checks every generated module with `node --check` and checks
app-operation boundaries. The selected web tests passed 60/60. Security and
whitespace checks passed. Full package test counts are recorded with release
verification outside Git.

Real iPhone home-screen authentication/storage, camera/scanning, local-network
permission, certificate trust and physical printing remain phone/hardware
acceptance. Individual app entry points remain available pending user acceptance.

