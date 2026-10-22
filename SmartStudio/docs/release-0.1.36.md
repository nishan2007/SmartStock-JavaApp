# SmartStudio 0.1.36 Windows release

User requested packaging and publication on 2026-10-17. Includes the editor changes since 0.1.28: Letter defaults, undo/redo, scalable page navigation, resizable inspector, text formatting, 300 DPI import export correction, automatic NFC login polling, and per-job page selection/single-sided defaults with optional paired duplex printing.

Validation: 100 Studio tests passed, no skips. Security and whitespace checks passed. Extracted the exact Inno installer; main and selected 1.1.40 dependency JAR hashes match build inputs. Extracted native launcher passed. Reviewed page-selection panel rendering. Installer is 225203550 bytes, SHA-256 b77c166d82b9e7e290b54c6a69a4442883f5595b4eaf20ee80f9d9cc0da172c9. Hosted bytes fully downloaded and verified before release metadata publication.

Requires the SmartStock 1.1.40 server update for Studio badge-status/PIN-setup authorization and new badge text style fields. Physical NFC tapping and real printer/duplex alignment remain unverified. Reader probe reported no compatible PC/SC reader on this computer. The running SmartStock server was not restarted or upgraded. Existing OneDrive template backups remain pending configuration repair; publication does not prove cloud template backup completion. No macOS package was built or published.
