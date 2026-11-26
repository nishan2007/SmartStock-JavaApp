// Rebuild a saved in-memory location from CURRENT authorized server listings.
// Saved labels, permissions and file details are never used as authority.
export async function resumeBrowsing(saved, api) {
    if (!saved || !Array.isArray(saved.folders) || !saved.folders.length || saved.folders.length > 64) return null;
    const stores = await api('stores');
    const root = stores.find(store => store.id === saved.folders[0]?.id);
    if (!root) return null;
    const folders = [{ ...root, name: (root.manage === false ? 'Shared from store ' : 'Store ') + root.store, folder: true }];
    for (const previous of saved.folders.slice(1)) {
        const entries = await api('files?id=' + encodeURIComponent(folders.at(-1).id));
        const folder = entries.find(entry => entry.id === previous?.id && entry.folder === true);
        if (!folder) return null;
        folders.push(folder);
    }
    let file;
    if (saved.fileId) {
        const entries = await api('files?id=' + encodeURIComponent(folders.at(-1).id));
        file = entries.find(entry => entry.id === saved.fileId && entry.folder === false);
    }
    return { folders, file };
}
