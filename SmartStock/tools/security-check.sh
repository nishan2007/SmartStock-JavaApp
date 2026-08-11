#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

fail() {
  printf 'SECURITY CHECK FAILED: %s\n' "$1" >&2
  exit 1
}

if rg -n --hidden -g '!target/**' -g '!*.md' -g '!security-check.sh' 'SmartStockClientLan2026!' .; then
  fail "legacy shared LAN password is present in source"
fi

if rg -n --hidden -g '!target/**' -g '!*.md' -g '!security-check.sh' \
  '(service[_-]?role|secret[_-]?key).{0,40}(eyJ[A-Za-z0-9_-]{20,}|sb_secret_)' .; then
  fail "a privileged Supabase key may be embedded in the repository"
fi

if git ls-files | rg '(^|/)(\.env($|\.)|database-credentials\.txt$|database\.properties$)'; then
  fail "a credential/config file is tracked by git"
fi

rg -Fq 'config.mode() == DatabaseMode.SERVER && !isLoopbackJdbcUrl(config.jdbcUrl())' src/data/DB.java \
  || fail "the server database URL is not restricted to loopback"
if rg -n 'getCloudConnection|SMARTSTOCK_CLOUD_DB_|cloud\.jdbc\.url|cloud\.db\.(user|password)' \
  src installer tools -g '!security-check.sh'; then
  fail "obsolete direct cloud PostgreSQL access is present"
fi
rg -q 'SecureCredentialStore' src/data/DatabaseConfig.java || fail "database config is not using secure credential storage"
rg -q 'ALTER TABLE public\.devices ENABLE ROW LEVEL SECURITY' \
  database/v1/cloud/001_schema.sql || fail "device table RLS is missing from the cloud v1 baseline"
rg -q 'REVOKE ALL ON FUNCTION public\.current_app_user_has_location' \
  database/v1/cloud/001_schema.sql || fail "store authorization is not restricted in the cloud v1 baseline"
rg -q 'HttpsServer' src/services/LanApiServer.java || fail "LAN HTTPS service is missing"
if rg -n 'data\.put\("pairingPhrase"|"pairingPhrase"[[:space:]]*,[[:space:]]*identity\.currentPairingPhrase' \
  src/services/LanApiServer.java src/services/LanDiscoveryService.java; then
  fail "the one-time administrator pairing phrase is exposed by an unauthenticated endpoint"
fi
if rg -n 'Pairing phrase:|pairing phrase:' src/app/SyncServiceMain.java; then
  fail "the one-time administrator pairing phrase is written to background-service status"
fi
rg -q 'HmacSHA256' src/services/LanSecurity.java || fail "pairing discovery is not authenticated with a certificate-bound proof"
rg -q 'pairingProofs\(\)' src/services/LanDiscoveryService.java || fail "LAN discovery does not publish the certificate-bound pairing proof"
rg -q 'DEVICE_CREDENTIAL_REQUIRED' src/services/LanApiServer.java || fail "LAN device authentication is missing"
rg -q 'requireDeviceHeaderBeforeBody' src/services/LanApiServer.java || fail "request bodies are parsed before device pre-authentication"
rg -q 'MAX_BODY_BYTES = 2 \* 1024 \* 1024' src/services/LanApiServer.java || fail "LAN request body limits are missing"
rg -q 'config\.mode\(\) == DatabaseMode\.CLIENT' src/data/DB.java \
  || fail "register JDBC is not blocked unconditionally"
rg -q 'return true;' src/services/LanCutoverPolicy.java \
  || fail "clean-break LAN policy can be disabled"
if rg -n 'GRANT .*smartstock_client|CREATE ROLE smartstock_client' src/services/ServerProvisioningService.java installer database -g '!*.md'; then
  fail "provisioning can recreate a legacy register database role"
fi
rg -q "listen_addresses = 'localhost'" src/services/PostgresRuntimeService.java \
  || fail "server provisioning does not bind PostgreSQL to loopback"
rg -q 'install_client_dependencies' installer/macos/install.command \
  || fail "the macOS register installer does not have a database-free path"
if sed -n '/install_client_dependencies()/,/^}/p' installer/macos/install.command | rg -q 'postgres|psql'; then
  fail "the macOS register dependency path still installs PostgreSQL"
fi
rg -q 'LoginSecurityService\.recordFailure\(connection, device\.deviceId\(\)' src/services/LanApiServer.java \
  || fail "failed employee logins are not attributed to the requesting device"
rg -q 'Idempotency-Key' src/services/LanApiServer.java \
  || fail "financial mutation idempotency is missing"
rg -q 'consumed_at=CURRENT_TIMESTAMP' src/services/LanApiServer.java \
  || fail "manager approvals are not consumed by the server"
rg -Fq 'v1/sales/refund' src/services/LanApiServer.java \
  || fail "server-side sale refund endpoint is missing"
rg -q 'String operationKey = "sales.refund.v1"' src/services/LanApiServer.java \
  || fail "sale refunds are not protected by operation-specific idempotency"
rg -Fq 'WHERE sale_id = ? AND location_id = ?' src/services/LanRefundService.java \
  || fail "sale refunds are not scoped to the employee store"
rg -q 'FOR UPDATE OF si' src/services/LanRefundService.java \
  || fail "sale item quantities are not locked during refunds"
rg -q 'RefundApprovalIdentity.withReason' src/services/LanApiServer.java \
  || fail "return approval is not bound to the exact server-validated resource"
rg -q 'LAN_SALE_RETURN_COMPLETED' src/services/LanRefundService.java \
  || fail "sale refunds do not write an immutable security audit event"
if rg -n 'DB\.getConnection\(|java\.sql\.' src/ui/screens/ReturnSale.java; then
  fail "return screen still has direct database access"
fi
for screen in src/ui/screens/MakeASale.java src/ui/screens/ViewSales.java; do
  if rg -n 'DB\.getConnection\(|java\.sql\.' "$screen"; then
    fail "POS screen still has direct database access: $screen"
  fi
done
for client_file in \
  src/ui/screens/EnterInventory.java \
  src/ui/screens/ViewInventory.java \
  src/ui/screens/ViewInventoryDetails.java \
  src/ui/screens/ReceivingHistory.java \
  src/ui/screens/StoreTransfer.java \
  src/ui/screens/DepartmentList.java \
  src/ui/screens/VendorList.java \
  src/ui/screens/NewItem.java \
  src/ui/screens/EditItem.java \
  src/ui/screens/PriceTagPrinting.java \
  src/ui/screens/CustomerTypeList.java \
  src/ui/components/DepartmentSelector.java \
  src/ui/components/VendorSelector.java \
  src/ui/components/ItemClassificationSelector.java \
  src/ui/components/ItemDetailsSelector.java; do
  if rg -n 'DB\.getConnection\(|java\.sql\.' "$client_file"; then
    fail "inventory client regained direct database access: $client_file"
  fi
done
if rg -n 'DB\.getConnection\(|java\.sql\.' src/ui/components/CustomerTypeSelector.java; then
  fail "customer type selector regained direct database access"
fi
for route in \
  /v1/products/edit-search \
  /v1/products/price-tags \
  /v1/products/price-tag-settings \
  /v1/products/create \
  /v1/products/update \
  /v1/catalog/customer-types/list \
  /v1/catalog/customer-types/save; do
  rg -Fq "${route#/}" src/services/LanApiServer.java \
    || fail "LAN product/customer catalog route is missing: $route"
done
rg -Fq 'String operation = create ? "products.create.v1" : "products.update.v1"' \
  src/services/LanApiServer.java || fail "product saves are not operation-idempotent"
rg -q 'String operation = "catalog.customer-types.save.v1"' src/services/LanApiServer.java \
  || fail "customer type saves are not operation-idempotent"
rg -Fq 'FOR UPDATE' src/services/LanProductAdminService.java \
  || fail "product edits do not lock rows before mutation"
rg -Fq 'request.expectedQuantity() != inventory.quantity()' src/services/LanProductAdminService.java \
  || fail "manual inventory edits can overwrite concurrent register activity"
rg -Fq 'quantity_on_hand=quantity_on_hand-?' src/services/LanSalesService.java \
  || fail "sales no longer use atomic relative inventory subtraction"
rg -Fq 'productIds.sort(Integer::compareTo)' src/services/LanSalesService.java \
  || fail "sales do not lock cart products in deterministic order"
if rg -n 'SyncServiceStatusService|WorkflowSyncIdentityCollisionVerifier' src/ui src/managers src/Receipt; then
  fail "server-only sync diagnostics became reachable from register code"
fi
if rg -n 'quantity_on_hand\s*>=\s*\?' src/services/LanSalesService.java src/services/LanTransferService.java; then
  fail "sales or transfers incorrectly reject negative inventory"
fi
if rg -n 'countedStock\(\)\s*<\s*0' src/services/LanInventoryService.java; then
  fail "receiving incorrectly rejects negative counted inventory"
fi
for route in \
  /v1/catalog/departments/list \
  /v1/catalog/departments/save \
  /v1/catalog/vendors/list \
  /v1/catalog/vendors/save; do
  rg -Fq "${route#/}" src/services/LanApiServer.java \
    || fail "LAN catalog administration route is missing: $route"
done
rg -q 'String operation = "catalog.departments.save.v1"' src/services/LanApiServer.java \
  || fail "department saves are not operation-idempotent"
rg -q 'String operation = "catalog.vendors.save.v1"' src/services/LanApiServer.java \
  || fail "vendor saves are not operation-idempotent"
rg -Fq 'LAN_DEPARTMENT_SAVED' src/services/LanCatalogAdminService.java \
  || fail "department changes are not security audited"
rg -Fq 'LAN_VENDOR_SAVED' src/services/LanCatalogAdminService.java \
  || fail "vendor changes are not security audited"
for route in \
  /v1/inventory/lookups \
  /v1/inventory/receiving-search \
  /v1/inventory/list \
  /v1/inventory/details \
  /v1/inventory/receiving-history \
  /v1/inventory/receive \
  /v1/transfers/destinations \
  /v1/transfers/products \
  /v1/transfers/incoming \
  /v1/transfers/items \
  /v1/transfers/create \
  /v1/transfers/receive; do
  rg -Fq "${route#/}" src/services/LanApiServer.java \
    || fail "LAN inventory/transfer route is missing: $route"
done
rg -q 'String operation="inventory.receive.v1"' src/services/LanApiServer.java \
  || fail "inventory receiving is not operation-idempotent"
rg -q 'String operation="transfers.create.v1"' src/services/LanApiServer.java \
  || fail "transfer creation is not operation-idempotent"
rg -q 'String operation="transfers.receive.v1"' src/services/LanApiServer.java \
  || fail "transfer receiving is not operation-idempotent"
rg -Fq 'i.location_id=?' src/services/LanTransferService.java \
  || fail "transfer product reads are not scoped to the employee store"
rg -Fq 'FOR UPDATE OF i' src/services/LanTransferService.java \
  || fail "transfer stock is not locked before mutation"
rg -Fq 'st.to_location_id=?' src/services/LanTransferService.java \
  || fail "incoming transfers are not scoped to the receiving store"
rg -q 'CrossStoreTransferSyncService\.announceTransfer' src/services/LanTransferService.java \
  || fail "store transfers do not publish complete cross-store envelopes"
rg -q 'CrossStoreTransferSyncService\.recordReceived' src/services/LanTransferService.java \
  || fail "store transfer receipts are not acknowledged to the source store"
rg -q "event_type='STORE_TRANSFER_RECEIVED'" database/migrations/v1_after/20260811233100_route_store_transfer_receipts.sql \
  || fail "store transfer receipt events are not routed back to the source store"
rg -q "'REFERENCE_ROW_CHANGED'" database/migrations/v1_after/20260811233100_route_store_transfer_receipts.sql \
  || fail "shared location and schedule rows are not routed to every store"
rg -q 'CrossStoreReferenceSyncService\.announceChanges' src/services/SyncWorker.java \
  || fail "shared location and schedule changes are not published by the sync worker"
rg -q 'CrossStoreReferenceSyncService\.applyInbox' src/services/SyncWorker.java \
  || fail "shared location and schedule changes are not applied by the sync worker"
rg -Fq 'FOR UPDATE' src/services/LanInventoryService.java \
  || fail "receiving does not lock current inventory before mutation"
rg -Fq 'v1/held-carts/create' src/services/LanApiServer.java \
  || fail "server-side held-cart creation endpoint is missing"
rg -Fq 'v1/held-carts/resume' src/services/LanApiServer.java \
  || fail "server-side held-cart resume endpoint is missing"
rg -q 'String operationKey = "held-carts.create.v1"' src/services/LanApiServer.java \
  || fail "held-cart creation is not operation-idempotent"
rg -q 'String operationKey = "held-carts.resume.v1"' src/services/LanApiServer.java \
  || fail "held-cart resume is not operation-idempotent"
rg -Fq 'WHERE held_cart_id=? AND location_id=?' src/services/LanHeldCartService.java \
  || fail "held-cart resume is not scoped to the employee store"
rg -Fq 'v1/sales/history' src/services/LanApiServer.java \
  || fail "store-scoped sales history endpoint is missing"
rg -Fq 'FROM sales WHERE sale_id=? AND location_id=?' src/services/LanSalesHistoryService.java \
  || fail "sales details are not scoped to the employee store"
rg -Fq 'hasPermission(connection, userId, "VIEW_SALE_AUDIT")' src/services/LanSalesHistoryService.java \
  || fail "sale audit history is not protected by its dedicated permission"
rg -q 'CREATE TABLE public\.lan_api_sessions' database/v1/local/001_schema.sql \
  || fail "LAN session table is missing from the local v1 baseline"
if rg -n 'v1/(sql|query|tables|rpc)' src/services/LanApiServer.java; then
  fail "a generic database endpoint is exposed by the LAN service"
fi

printf 'SmartStock repository security checks passed.\n'
