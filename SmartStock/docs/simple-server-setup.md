# SmartStock Server Setup

Use the packaged SmartStock application on the new Windows server. Do not
install Maven. SmartStock includes the Java runtime it needs.

1. Install and open SmartStock.
2. Choose **Developer / Test** or **Production**, then choose **Store Server**.
3. Click **Continue to Server Setup**. SmartStock opens the resumable six-step
   Server Setup Wizard.
4. In **Connect Supabase**, enter the project URL, publishable key, and
   server-only `sb_secret_` key. Click **Test and Continue**.
5. In **Initialize Cloud**, SmartStock first checks the existing schema. If the
   schema is already current, continue without a database password. Otherwise,
   paste the Supabase Direct or Session Pooler connection on port 5432 and
   enter the project database password once. Port 6543 is rejected. These
   one-time database details are immediately cleared and never saved.
6. In **Prepare Local Database**, SmartStock checks PostgreSQL 15 or newer and
   installs it when needed. It generates and securely saves the private
   `smartstock_server` database account automatically. If PostgreSQL asked you
   to choose an administrator password during installation, enter that password
   once when this step displays the fallback field. Users do not enter JDBC
   URLs, database names, or SmartStock application passwords.
7. In **Create or Select Store**, select an existing store or enter the first
   store's name, four-digit code, timezone, and optional address. SmartStock
   assigns the numeric database ID automatically.
8. In **Create First Administrator**, choose **Transfer Existing
   Administrator** or **Create New Administrator**.
   A transfer lists active Development-profile administrators and preserves
   the selected badge identity. The administrator enters their password
   privately to create an independent production Auth identity.
9. In **Start and Verify Server**, click **Start and Verify** and approve the
   Windows administrator prompt. SmartStock installs automatic startup, uses
   `LocalSubnet` for the private-network firewall rule, starts LAN and
   synchronization services, and verifies local and cloud access.
10. Continue to login and have the first administrator sign in online once.
    This creates the local offline verifier through normal authenticated login.
11. Restart Windows.
12. Open SmartStock and use **Refresh Service Status**. Confirm that Local
    Database and LAN Service both show **Running**.
13. On each register, choose **Register**, find or enter the server address,
    and complete the one-time administrator pairing.
14. Run the production readiness, backup restore, cloud recovery, user login,
    badge, role, and peripheral checks before accepting live sales.

The wizard checks saved state whenever it opens and resumes at the first
incomplete step. **Advanced Settings** remains available for technical repair,
but it is not part of normal first-time setup.

Register and Remote Admin computers do not install PostgreSQL and never receive
database credentials. The Supabase server key remains in the server operating
system credential store and is never sent to registers. All production
computers use the packaged Java runtime.
