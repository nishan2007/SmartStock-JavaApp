# WhatsApp Cloud API setup

SmartStock sends sales documents through Meta's WhatsApp Cloud API. Registers never receive the Cloud API access token.

## Meta preparation

1. Complete Meta Business and WhatsApp Business Platform onboarding.
2. Choose either a dedicated API phone number or Meta's supported WhatsApp Business App coexistence/migration route.
3. Create utility templates whose body contains one text variable. SmartStock supplies the selected document summary as that variable.
4. Obtain the Cloud API phone-number ID and a permanent system-user access token with the required WhatsApp messaging permission.

## Store server

Set `SMARTSTOCK_WHATSAPP_ACCESS_TOKEN` in the store server/service environment and restart the service. Do not put this value in the database, application preferences, scripts, or source control.

In **Location Management → WhatsApp Settings**, configure:

- Cloud API phone-number ID and Graph API version.
- Message detail: `FULL`, `COMPACT`, or `REFERENCE`.
- Template language.
- Estimated cost per accepted message and monthly warning budget.
- Customer contact line.
- Template names as a JSON object. Keys combine document type and detail mode, for example:

```json
{
  "SALE_RECEIPT_FULL": "smartstock_sale_receipt_full",
  "QUOTATION_FULL": "smartstock_quotation_full",
  "INVOICE_FULL": "smartstock_invoice_full",
  "DELIVERY_BILL_FULL": "smartstock_delivery_bill_full",
  "ACCOUNT_PAYMENT_RECEIPT_FULL": "smartstock_account_payment_full"
}
```

Add equivalent keys for `COMPACT` and `REFERENCE` before selecting those modes.

## Customer consent and sending

Record consent on the customer account only after the customer explicitly agrees to receive sales documents from the named business through WhatsApp. Changing the saved phone number updates the consent snapshot; clearing the checkbox opts the customer out immediately.

Open a supported document preview and select **Send WhatsApp**. SmartStock reports only whether Meta accepted the message. Delivery and read receipts require a public webhook and are not part of this release.

The displayed budget warning is an estimate based on accepted messages and the configured rate. Meta billing remains authoritative.
