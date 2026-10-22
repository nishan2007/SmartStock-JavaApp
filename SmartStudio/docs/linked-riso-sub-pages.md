# Linked Riso sub-pages

Open Business Cards or Labels and choose **Riso → Linked Riso sub-pages…**. The organiser shows each main page and its linked output views. Existing automatic colour separation remains under **Riso → Automatic colour separation…**.

Select whole editable objects in the main design or object list, then use **Add sub-page** to create a sub-page containing them. Alternatively add an empty sub-page, select objects, choose its name under **Move selected to**, and click **Move objects**. Ctrl/Shift-click and box selection support groups. Object-list rows can also be dragged onto a sub-page. Rename sub-pages and set their ink colours as needed. Each object belongs to one sub-page per main page.

The main design retains the only copies of object geometry, text, artwork and styling. Sub-pages contain stable object IDs. Moving, resizing, editing or recolouring an existing source object updates every linked view. Reordering layers preserves membership. Edits made while viewing a sub-page change the same original objects. Shared Publisher master objects are also supported; their geometry remains shared across main pages.

New objects start **Unassigned**. Move unwanted objects to **Not printing** to exclude them intentionally. Output is blocked while objects remain unassigned, so additions cannot silently disappear. Removing a sub-page returns its objects to Unassigned without deleting the source artwork. Delete in a sub-page unassigns its selected objects; Delete in the main design deletes the originals. Undo/Redo operate within the organiser. **Apply to template** commits the transaction to the main workspace; **Cancel** discards it. Save the template normally to keep assignments across sessions and shared-library synchronization.

The page sidebar shows sub-pages beneath their main page, with previews that refresh after source edits. Clicking a child opens its organiser view. Previews can be resized with the thumbnail slider, and panes can be resized.

Choose **Print sub-pages…** or **Export sub-pages PDF…**, then select the desired sub-pages of the current main page. Each uses the same paper, scale, placement, quantity, merge data and cut marks from Print setup. Output is single-sided; all assigned artwork is converted to grayscale coverage for that sub-page's ink. PDF is lossless 300 dpi grayscale at actual paper size, ordered by sub-page then sheet. Ink colours are approximate preview colours; actual Riso drums supply the ink. Printer size/printable-area checks prevent scaling or clipped marks.

Linked templates use publication format **2**, requiring **SmartStudio 0.1.43** and **SmartStock server 1.1.45** for shared-library saves. Existing format-1 templates remain supported. Older clients reject format 2 rather than open and resave it while losing memberships. Update the server first, then clients before sharing linked templates.

This version organises whole editable objects. Splitting areas within a single image is deferred; the stable source-object links permit adding that capability later without duplicating the main design.
