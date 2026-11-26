package gy.deckers.studio;

import javax.swing.AbstractButton;
import javax.swing.JButton;

/** Shared ribbon placement and presentation for commands supplied by each workspace. */
enum StudioCommand {
    CHANGE_TEMPLATE("Home", "Template", "Change template", 0xf07c),
    UNDO("Home", "History", "Undo", 0xf0e2),
    REDO("Home", "History", "Redo", 0xf01e),
    COPY("Home", "Clipboard", "Copy layers", 0xf0c5),
    PASTE("Home", "Clipboard", "Paste", 0xf0ea),
    DELETE("Home", "Clipboard", "Remove selected layers", 0xf2ed),
    QUICK_TEXT("Home", "Insert", "Add text", 0xf031),
    QUICK_IMAGE("Home", "Insert", "Add image", 0xf03e),
    FORWARD("Home", "Arrange", "Bring forward", 0xf062),
    BACKWARD("Home", "Arrange", "Send backward", 0xf063),
    TO_FRONT("Home", "Arrange", "Bring to front", 0xf5fd),
    TO_BACK("Home", "Arrange", "Send to back", 0xf5fc),
    ALIGN("Home", "Arrange", "Align…", 0xf036),
    DISTRIBUTE("Home", "Arrange", "Distribute…", 0xf07d),
    TEXT("Insert", "Text", "Text box", 0xf031),
    WORDART("Insert", "Text", "WordArt…", 0xf031),
    IMAGE("Insert", "Media", "Image", 0xf03e),
    PHOTO_FIELD("Insert", "Media", "Add photo field", 0xf030),
    RECTANGLE("Insert", "Shapes", "Rectangle", 0xf0c8),
    ELLIPSE("Insert", "Shapes", "Ellipse", 0xf111),
    TRIANGLE("Insert", "Shapes", "Triangle", 0xf0d8),
    LINE("Insert", "Shapes", "Line", 0xf068),
    BARCODE("Insert", "Codes", "Add barcode…", 0xf02a),
    QR("Insert", "Codes", "Add QR code…", 0xf029),
    TEXT_FIELD("Insert", "Fields", "Add text field…", 0xf031),
    MERGE_FIELD("Insert", "Fields", "Merge field…", 0xf15c),
    PAGE_NUMBER("Insert", "Fields", "Page number", 0xf292),
    TABLE("Insert", "Tables", "Insert table…", 0xf0ce),
    ADD_PAGE("Insert", "Pages", "Add page", 0xf15b),
    DELETE_PAGE("Insert", "Pages", "Delete page", 0xf2ed),
    BROWSE_RECORDS("Data", "Records", "DB", 0xf1c0),
    LAYERS("View", "Panels", "Layers", 0xf5fd),
    ZOOM_OUT("View", "Zoom", "Zoom out", 0xf010),
    FIT("View", "Zoom", "Fit", 0xf065),
    ZOOM_IN("View", "Zoom", "Zoom in", 0xf00e);

    final String tab;
    final String group;
    final String label;
    final int glyph;

    StudioCommand(String tab, String group, String label, int glyph) {
        this.tab = tab;
        this.group = group;
        this.label = label;
        this.glyph = glyph;
    }

    JButton shortcut(AbstractButton source) {
        JButton button = StudioRibbon.shortcut(source, label);
        button.putClientProperty(StudioCommand.class, this);
        return button;
    }

    JButton button(Runnable action) {
        JButton button = StudioRibbon.button(label, action);
        button.putClientProperty(StudioCommand.class, this);
        return button;
    }

    static StudioCommand layerOrder(StudioLayerOrder.Move move) {
        return switch (move) {
            case FORWARD -> FORWARD;
            case BACKWARD -> BACKWARD;
            case FRONT -> TO_FRONT;
            case BACK -> TO_BACK;
        };
    }
}
