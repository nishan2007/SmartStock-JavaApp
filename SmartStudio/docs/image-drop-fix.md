# Image Studio native drop handoff

SmartStudio now explicitly accepts a native COPY drop, captures its data while
the drop is active, calls `dropComplete`, and only then queues image loading and
UI changes. Drag-over checks use advertised formats rather than requesting the
native transferable. File reading remains in the background. Image transfers are
copied into a detached ARGB bitmap before completion so lazy native images cannot
outlive Windows' transfer state. PNG-only stream transfers are also supported,
with the existing input size and dimension limits.

Drops are accepted on the Original image preview. Saved files, standard image
data and PNG streams are supported; web links and application-specific virtual
attachments are not automatically fetched. If WhatsApp does not provide one of
these formats, save the picture and use Choose image.

Version 0.1.8 registers Windows PNG transfers before any incoming drag, and tries
other advertised image representations if native file or bitmap retrieval fails.
Paste image and Ctrl+V within Image Studio accept copied images. In WhatsApp Web,
open the photo, right-click Copy image, then use Paste image. A browser-only blob
reference cannot be fetched outside that browser's originating storage context;
the app does not fetch browser URLs or claim that every Web drag supplies pixels.

Regression checks simulate a native transfer that is unreadable before acceptance
and after completion, invalidated source image pixels, failed transfers, deferred
error handling and busy/move-only rejection. The SmartStudio suite, Java build,
security check and whitespace check pass. Actual dragging from WhatsApp and the
installed Windows application remain separate manual checks. This source change
does not update an installed application or publish a release.
