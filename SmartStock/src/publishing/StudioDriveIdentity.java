package publishing;

import java.util.Set;

/** Portable logical identity, independent of the filename or Windows disk path. */
public final class StudioDriveIdentity {
    public String id, storageStoreId, kind;
    public StudioDriveIdentity(String id,String storageStoreId,String kind) {
        this.id=id;this.storageStoreId=storageStoreId;this.kind=kind;validate();
    }
    public static StudioDriveIdentity forNewFile(java.nio.file.Path file,String kind) {
        java.nio.file.Path path=file.toAbsolutePath().normalize();
        if(path.getNameCount()<2)return null;
        // Standard UNC and mapped staff shares both expose the owning store as
        // their first path component. Local exports need no drive identity.
        String store=path.getName(0).toString();
        if(!store.matches("[1-9][0-9]{0,8}"))return null;
        return new StudioDriveIdentity(java.util.UUID.randomUUID().toString(),store,kind);
    }
    public static StudioDriveIdentity fromFile(java.nio.file.Path file)throws java.io.IOException {
        var view=java.nio.file.Files.getFileAttributeView(file,java.nio.file.attribute.UserDefinedFileAttributeView.class);
        if(view==null||!view.list().contains("SmartDriveIdentity"))return null;
        int size=view.size("SmartDriveIdentity");
        if(size<1||size>4096)throw new java.io.IOException("Invalid SmartDrive file identity metadata.");
        var buffer=java.nio.ByteBuffer.allocate(size);view.read("SmartDriveIdentity",buffer);buffer.flip();
        try {
            var value=Publication.JSON.fromJson(java.nio.charset.StandardCharsets.UTF_8.decode(buffer).toString(),StudioDriveIdentity.class);
            value.validate();return value;
        } catch(RuntimeException invalid){throw new java.io.IOException("Invalid SmartDrive file identity metadata.",invalid);}
    }
    public void validate() {
        if(id==null||!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")||
                storageStoreId==null||!storageStoreId.matches("[1-9][0-9]{0,8}")||kind==null||
                !Set.of("BADGE","BUSINESS_CARDS","LABELS","BILL_BOOKS").contains(kind))
            throw new IllegalArgumentException("Invalid SmartDrive template identity.");
    }
}
