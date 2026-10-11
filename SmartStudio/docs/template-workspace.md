# Template workspace

SmartStudio template editors should use `TemplateWorkspace` and its `Design`
adapter for the shared editable pasteboard, including future template types.
Coordinates are in millimetres relative to the top left of the printable page.
The white area is the print boundary; surrounding grey space stores editable
layers that can be dragged back onto the page. The layer table also supports
negative X/Y positions and positions beyond the page edges.

Save all layers, including parked layers. Output renderers must clip to the page
dimensions: fully parked layers produce no output, and partially overlapping
layers print only their intersection with the page. Workspace selection borders
and the surrounding pasteboard are editor decorations and must never be exported.

The badge editor uses the shared workspace and rendering implementation today.
New template editors supply page dimensions, layer bounds, movement, and painting
through `TemplateWorkspace.Design`.
