package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.Publication;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class TemplatePageNavigationTest {
    @Test void pageControlsStayVisibleWhenSinglePageBecomesMultiplePages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (BusinessCardsPanel editor : new BusinessCardsPanel[]{
                    new BusinessCardsPanel(), new BusinessCardsPanel(true),
                    new BusinessCardsPanel(false, false, true), new BusinessCardsPanel(false, true)}) {
                try {
                    var field=BusinessCardsPanel.class.getDeclaredField("pagePreviews");
                    field.setAccessible(true);
                    PublicationPages navigation=(PublicationPages)field.get(editor);
                    Publication document=editor.output().copy();
                    while(document.pages.size()>1)document.pages.remove(document.pages.size()-1);
                    navigation.update(document,0);
                    assertTrue(navigation.isVisible(),"Single-page controls must reserve navigation space");
                    JSplitPane split=(JSplitPane)navigation.getParent();
                    split.setSize(900,600);split.doLayout();
                    document.pages.add(new Publication.Page());
                    navigation.update(document,0);split.doLayout();
                    assertTrue(navigation.isVisible());
                    assertTrue(navigation.getWidth()>=110,"Opening multiple pages must not leave navigation collapsed");
                    assertEquals(2,navigation.list.getModel().getSize());
                    navigation.update(editor.output(),0);
                } catch (ReflectiveOperationException ex) {throw new RuntimeException(ex);}
            }
        });
    }
}
