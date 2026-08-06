#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="$ROOT_DIR/src"

# Server-owned packages are the only places allowed to retain JDBC after the
# single cutover. Any register-callable database path fails the build and the
# pre-launch installer leaves no register database login enabled.
violations="$({
  rg -l 'import data\.DB;|import java\.sql\.(Connection|PreparedStatement|ResultSet|Statement);|DB\.getConnection\(|DriverManager\.getConnection\(' "$SRC_DIR/ui" "$SRC_DIR/Receipt" 2>/dev/null \
    | rg -v ' 2\.java$|/Server[^/]*\.java$' || true
  rg -l 'import data\.DB;|import java\.sql\.(Connection|PreparedStatement|ResultSet|Statement);|DB\.getConnection\(|DriverManager\.getConnection\(' "$SRC_DIR/managers" 2>/dev/null \
    | rg -v ' 2\.java$|/(ServerTimeClockManager|ServerCompanyCustomizationRepository|ServerReceiptNumberManager)\.java$' || true
  rg -l 'DB\.getConnection\(|DriverManager\.getConnection\(' "$SRC_DIR/services" 2>/dev/null \
    | rg -v ' 2\.java$|/(LanApiServer|ServerNotificationService|ServerEmployeeScheduleService|ServerEmployeeAutoScheduleService|ServerCustomOrderDataService|ServerQuotationInvoiceService|ServerQuotationInvoiceViewService|ServerBalanceSheetService|ServerEmailOutboxService|ServerImageAssetService|ServerImageAssetMaintenance|ServerStoreSetupService|ServerFirstAdministratorService|ServerSupabaseMigrationRunner|CloudServerRegistryService|ServerManagementClient|ServerSetupGuardService|SyncWorker|SyncServiceStatusService|WorkflowSyncIdentityCollisionVerifier|ServerProvisioningService|LocalDatabaseBootstrapService|CompanyBackupService|LocalServerRepairService|.*SchemaInstaller|ReferenceDataSyncService)\.java$' || true
} | sort -u)"

# Client facades must stay transport-only even though their server-side DTO
# counterparts live in the same source tree.
for facade in CompanyCustomizationManager TimeClockManager ReceiptNumberManager \
              BalanceSheetService EmailOutboxService QuotationInvoiceService \
              QuotationInvoiceViewService CustomOrderDataService NotificationService; do
  file="$SRC_DIR/managers/$facade.java"
  [[ -f "$file" ]] || file="$SRC_DIR/services/$facade.java"
  [[ -f "$file" ]] || continue
  if rg -n 'import data\.DB;|DB\.getConnection\(|DriverManager\.getConnection\(' "$file" >/dev/null 2>&1; then
    printf '%s\n' "LAN API cutover blocked: client facade $facade contains database access." >&2
    exit 1
  fi
done

if rg -n 'ServerTimeClockManager' "$SRC_DIR/ui" "$SRC_DIR/Receipt" >/dev/null 2>&1; then
  printf '%s\n' "LAN API cutover blocked: register code imports the server time-clock repository." >&2
  exit 1
fi

if rg -n 'ServerNotificationService' "$SRC_DIR/ui" "$SRC_DIR/managers" "$SRC_DIR/Receipt" >/dev/null 2>&1; then
  printf '%s\n' "LAN API cutover blocked: register code imports the server notification repository." >&2
  exit 1
fi

if rg -n 'ServerEmployee(Schedule|AutoSchedule)Service' "$SRC_DIR/ui" "$SRC_DIR/managers/TimeClockManager.java" "$SRC_DIR/Receipt" >/dev/null 2>&1; then
  printf '%s\n' "LAN API cutover blocked: register code imports a server schedule repository." >&2
  exit 1
fi

if rg -n 'ServerCustomOrderDataService' "$SRC_DIR/ui" "$SRC_DIR/managers" "$SRC_DIR/Receipt" >/dev/null 2>&1; then
  printf '%s\n' "LAN API cutover blocked: register code imports the server custom-order repository." >&2
  exit 1
fi

if rg -n 'ServerQuotationInvoice(Service|ViewService|DocumentBuilder)' "$SRC_DIR/ui" "$SRC_DIR/managers" >/dev/null 2>&1; then
  printf '%s\n' "LAN API cutover blocked: register code imports a server quotation repository." >&2
  exit 1
fi

if rg -n 'Server(BalanceSheetService|EmailOutboxService|CompanyCustomizationRepository|ReceiptNumberManager)' \
     "$SRC_DIR/ui" "$SRC_DIR/Receipt" -g '!Server*.java' >/dev/null 2>&1; then
  printf '%s\n' "LAN API cutover blocked: register code imports a server-only repository." >&2
  exit 1
fi

if rg -n 'SMARTSTOCK_CLIENT_DB_(USER|PASSWORD)|clientJdbcUrlOrDefault|Server PostgreSQL Port|Scanning this network for PostgreSQL' \
     "$SRC_DIR/ui" >/dev/null 2>&1; then
  printf '%s\n' "LAN API cutover blocked: the register setup screen still exposes database credentials or discovery." >&2
  exit 1
fi

if [[ -n "$violations" ]]; then
  printf '%s\n' "LAN API cutover blocked: register-callable JDBC remains:" >&2
  printf '%s\n' "$violations" >&2
  exit 1
fi

printf '%s\n' "LAN API cutover check passed: no register-callable JDBC paths remain."
