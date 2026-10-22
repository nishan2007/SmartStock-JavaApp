"""Local isolated LibreOffice worker. Documents cannot execute macros or update links."""
import json, os, pathlib, subprocess, sys, tempfile, time, uuid
import uno
from com.sun.star.beans import PropertyValue
from com.sun.star.document.MacroExecMode import NEVER_EXECUTE

def props(**values):
    return tuple(PropertyValue(Name=k, Value=v) for k, v in values.items())

def export(source, destination, soffice):
    destination = pathlib.Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    profile = destination / 'profile'
    pipe = 'studio_' + uuid.uuid4().hex
    env = dict(os.environ, SAL_DISABLE_PRINTERLIST='1', SAL_DISABLE_DEFAULTPRINTER='1')
    process = subprocess.Popen([soffice, '-env:UserInstallation=' + profile.as_uri(), '--headless', '--norestore', '--nodefault', '--nofirststartwizard', '--accept=pipe,name=' + pipe + ';urp;StarOffice.ServiceManager'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    desktop = doc = None
    try:
        local = uno.getComponentContext()
        resolver = local.ServiceManager.createInstanceWithContext('com.sun.star.bridge.UnoUrlResolver', local)
        remote = None
        for _ in range(120):
            try:
                remote = resolver.resolve('uno:pipe,name=' + pipe + ';urp;StarOffice.ComponentContext')
                break
            except Exception:
                time.sleep(.5)
        if remote is None:
            raise RuntimeError('The local LibreOffice converter did not start.')
        desktop = remote.ServiceManager.createInstanceWithContext('com.sun.star.frame.Desktop', remote)
        doc = desktop.loadComponentFromURL(pathlib.Path(source).as_uri(), '_blank', 0, props(Hidden=True, ReadOnly=True, MacroExecutionMode=NEVER_EXECUTE, UpdateDocMode=0))
        if doc is None or not hasattr(doc, 'getDrawPages'):
            raise RuntimeError('This file does not contain supported drawing pages.')
        exporter = remote.ServiceManager.createInstanceWithContext('com.sun.star.drawing.GraphicExportFilter', remote)
        pages = doc.getDrawPages()
        if not 0 < pages.getCount() <= 100:
            raise RuntimeError('Import supports 1 to 100 pages.')
        output = {'pages': []}
        objects = 0
        def layers(container, output, ancestors=(), master=False):
            nonlocal objects
            for i in range(container.getCount()):
                shape = container.getByIndex(i)
                # Master placeholders describe slide layout; exporting them directly
                # prints prompts such as "Click to edit Master title style" over artwork.
                if master and shape.ShapeType.startswith('com.sun.star.presentation.'):
                    continue
                source_bounds = dict(x=shape.Position.X/100,y=shape.Position.Y/100,width=shape.Size.Width/100,height=shape.Size.Height/100)
                if shape.supportsService('com.sun.star.drawing.GroupShape'):
                    layers(shape, output, ancestors+(source_bounds,), master=master)
                    continue
                objects += 1
                if objects > 4000:
                    raise RuntimeError('Too many objects in this template.')
                rect = shape.BoundRect
                if rect.Width <= 0 or rect.Height <= 0:
                    continue
                text = getattr(shape, 'String', '')
                item = dict(x=rect.X/100, y=rect.Y/100, width=rect.Width/100, height=rect.Height/100, text=text, sourceBounds=list(ancestors)+( [source_bounds] ))
                if text:
                    cursor = shape.createTextCursor()
                    item.update(font=cursor.CharFontName or 'SansSerif', fontSize=float(cursor.CharHeight or 12), bold=(cursor.CharWeight or 0) >= 150, color=int(cursor.CharColor if cursor.CharColor is not None else 0))
                target = destination / ('object-' + str(objects) + '.png')
                exporter.setSourceDocument(shape)
                pixels = max(1, min(5000, round(rect.Width / 2540 * 300)))
                filters = props(Translucent=True, PixelWidth=pixels, PixelHeight=max(1,min(5000,round(pixels*rect.Height/rect.Width))))
                if not exporter.filter(props(URL=target.as_uri(), MediaType='image/png', FilterData=uno.Any('[]com.sun.star.beans.PropertyValue', filters))):
                    raise RuntimeError('Cannot export an individual drawing object.')
                item['image'] = target.name
                if shape.ShapeType == 'com.sun.star.drawing.TableShape':
                    table = shape.Model
                    widths = [table.Columns.getByIndex(c).Width for c in range(table.Columns.Count)]
                    heights = [table.Rows.getByIndex(r).Height for r in range(table.Rows.Count)]
                    cells = []
                    covered = set()
                    for r in range(len(heights)):
                        for c in range(len(widths)):
                            if (r,c) in covered:
                                continue
                            cell = table.getCellByPosition(c,r)
                            rows = max(1,cell.getRowSpan())
                            columns = max(1,cell.getColumnSpan())
                            for rr in range(r,r+rows):
                                for cc in range(c,c+columns):
                                    if (rr,cc)!=(r,c): covered.add((rr,cc))
                            cursor = cell.createTextCursor()
                            cells.append(dict(row=r,column=c,rowSpan=rows,columnSpan=columns,text=cell.String,
                                left=sum(widths[:c])/sum(widths),top=sum(heights[:r])/sum(heights),
                                width=sum(widths[c:c+columns])/sum(widths),height=sum(heights[r:r+rows])/sum(heights),
                                font=cursor.CharFontName or 'SansSerif',fontSize=float(cursor.CharHeight or 12),bold=bool((cursor.CharWeight or 0)>=150),
                                color=max(0,int(cursor.CharColor if cursor.CharColor is not None else 0)),fill=int(cell.FillColor if cell.FillColor is not None else 0xffffff)))
                    item['cells']=cells
                kind=shape.ShapeType.rsplit('.',1)[-1]
                if not text and kind in ('RectangleShape','EllipseShape','LineShape') and not getattr(shape,'RotateAngle',0):
                    item.update(kind={'RectangleShape':'RECT','EllipseShape':'ELLIPSE','LineShape':'LINE'}[kind],fill=int(getattr(shape,'FillColor',0xffffff)),stroke=int(getattr(shape,'LineColor',0)),strokeWidth=float(getattr(shape,'LineWidth',25))/100,
                        noFill=getattr(getattr(shape,'FillStyle',None),'value','NONE')=='NONE',noStroke=getattr(getattr(shape,'LineStyle',None),'value','NONE')=='NONE')
                output.append(item)
        for i in range(pages.getCount()):
            page = pages.getByIndex(i)
            record = dict(width=page.Width/100, height=page.Height/100, objects=[])
            # Preserve background/master artwork separately, before this page's own objects.
            master = getattr(page, 'MasterPage', None)
            if master is not None:
                layers(master, record['objects'], master=True)
            layers(page, record['objects'])
            output['pages'].append(record)
        # Comparison reference is retained outside the editable template.
        doc.storeToURL((destination/'source-reference.pdf').as_uri(), props(FilterName='draw_pdf_Export' if doc.supportsService('com.sun.star.drawing.DrawingDocument') else 'impress_pdf_Export', Overwrite=True))
        (destination/'layers.json').write_text(json.dumps(output), encoding='utf-8')
    finally:
        if doc is not None:
            try: doc.close(True)
            except Exception: pass
        if desktop is not None:
            try: desktop.terminate()
            except Exception: pass
        try: process.wait(timeout=5)
        except Exception: process.kill()

if __name__ == '__main__':
    export(sys.argv[1], sys.argv[2], sys.argv[3])
