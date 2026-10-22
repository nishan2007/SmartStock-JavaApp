# Selecting multiple template objects

In Business Cards, Labels and Badge Cards:

- Ctrl-click or Shift-click to add/remove an object from the selection.
- Drag from empty canvas space to select intersecting objects. Hold Ctrl/Shift to add that box selection to the existing selection.
- Ctrl+A selects all objects while the canvas has focus.
- Drag a selected object or use arrow keys to move the group without changing its spacing. Arrow keys move 0.1 mm; Shift+arrow moves 1 mm.
- Delete or **Remove selected layers** removes the selected objects. Undo restores the group edit in one step.

The Layers table also supports multiple selection. Selected objects each have a border; resize handles and individual formatting controls appear only for a single selected object. Locked publication objects can be selected but cannot be moved, resized, edited inline or deleted from the canvas.

# Recolouring template artwork

Select one image and choose **Recolour picture…** in the top picture toolbar. Available modes:

- **Tint whole picture** maps dark areas to the chosen colour and, with shading enabled, preserves lighter tones and white.
- **Replace one colour** changes pixels near the source colour. Click the original preview to sample the source, or use its colour chooser; tolerance controls the range of matching colours. Shading preservation retains nearby channel differences.
- **Grayscale** removes colour while retaining transparency.

The dialog previews the result before applying. Cancel leaves the image unchanged. Applying creates replacement PNG artwork without changing object size, position, crop or rotation. Other objects sharing the original image are unchanged. Undo restores the original artwork; saved/exported templates contain the edited image. Badge personal-photo fields are separate from template artwork and do not use this recolour command.
