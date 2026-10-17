package local.qqslim.module;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.ArrayList;
import java.util.List;

/** Trusted root aliases are resolved once; all descendants use fd-relative no-follow I/O. */
final class FileOps {
    private static final int MAX_DEPTH=128;
    private static final int MAX_ENTRIES=1_000_000;

    private static final class Budget {
        int entries;
        void check(int depth) throws IOException {
            if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled");
            if(depth>MAX_DEPTH||++entries>MAX_ENTRIES)throw new IOException("Traversal limit");
        }
    }

    private static final class Anchor implements AutoCloseable {
        final List<DirectoryStream<Path>> opened=new ArrayList<>();
        SecureDirectoryStream<Path> selected;
        @Override public void close() throws IOException {
            IOException failure=null;
            for(int i=opened.size()-1;i>=0;i--)try{opened.get(i).close();}catch(IOException e){failure=e;}
            if(failure!=null)throw failure;
        }
    }

    private static Anchor open(File file,File internal,File external) throws IOException {
        Path path=file.toPath().toAbsolutePath().normalize();
        Path g=internal.toPath().toAbsolutePath().normalize();
        Path e=external.toPath().toAbsolutePath().normalize();
        Path root=path.startsWith(g)?g:e;
        if(path.equals(root)||!path.startsWith(root))throw new IOException("Outside cache roots");
        Path relative=root.relativize(path);
        if(relative.getNameCount()>MAX_DEPTH)throw new IOException("Directory depth limit");
        Anchor anchor=new Anchor();
        try {
            // /sdcard and /data/user/0 may be trusted Android aliases. Descendants are not.
            DirectoryStream<Path> stream=Files.newDirectoryStream(root.toRealPath());
            anchor.opened.add(stream);
            if(!(stream instanceof SecureDirectoryStream))throw new IOException("Secure directory I/O unsupported");
            anchor.selected=(SecureDirectoryStream<Path>)stream;
            for(Path part:relative){
                if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled");
                anchor.selected=anchor.selected.newDirectoryStream(part,LinkOption.NOFOLLOW_LINKS);
                anchor.opened.add(anchor.selected);
            }
            return anchor;
        } catch(IOException|RuntimeException ex){
            try{anchor.close();}catch(IOException suppressed){ex.addSuppressed(suppressed);}
            throw ex;
        }
    }

    static long size(File file,File internal,File external) throws IOException {
        Anchor anchor;
        try{anchor=open(file,internal,external);}catch(NoSuchFileException absent){return 0;}
        try(Anchor opened=anchor){return scan(opened.selected,0,new Budget());}
    }

    private static BasicFileAttributes attributes(SecureDirectoryStream<Path> dir,Path name) throws IOException {
        BasicFileAttributeView view=dir.getFileAttributeView(name,BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(view==null)throw new IOException("Missing attribute view");
        BasicFileAttributes a=view.readAttributes();
        if(a.isSymbolicLink()||(!a.isDirectory()&&!a.isRegularFile()))throw new IOException("Link or special file");
        return a;
    }

    private static long scan(SecureDirectoryStream<Path> dir,int depth,Budget budget) throws IOException {
        budget.check(depth);long total=0;
        for(Path entry:dir){
            budget.check(depth);Path name=entry.getFileName();
            BasicFileAttributes a=attributes(dir,name);
            long bytes;
            if(a.isDirectory()){
                try(SecureDirectoryStream<Path> child=dir.newDirectoryStream(name,LinkOption.NOFOLLOW_LINKS)){
                    bytes=scan(child,depth+1,budget);
                }
            }else bytes=a.size();
            if(bytes<0||Long.MAX_VALUE-total<bytes)throw new IOException("Size overflow");
            total+=bytes;
        }
        return total;
    }

    static boolean clear(File file,File internal,File external){
        Anchor anchor;
        try{anchor=open(file,internal,external);}
        catch(NoSuchFileException absent){return true;}
        catch(IOException|RuntimeException error){return false;}
        try(Anchor opened=anchor){
            // Preflight before the first deletion; a second stream avoids reusing an iterator.
            scan(opened.selected,0,new Budget());
            Path dot=file.toPath().getFileSystem().getPath(".");
            try(SecureDirectoryStream<Path> fresh=opened.selected.newDirectoryStream(dot,LinkOption.NOFOLLOW_LINKS)){
                clearChildren(fresh,0,new Budget());
            }
            return true;
        }catch(IOException|RuntimeException error){return false;}
    }

    private static void clearChildren(SecureDirectoryStream<Path> dir,int depth,Budget budget) throws IOException {
        budget.check(depth);
        for(Path entry:dir){
            budget.check(depth);Path name=entry.getFileName();
            BasicFileAttributes a=attributes(dir,name);
            if(a.isDirectory()){
                try(SecureDirectoryStream<Path> child=dir.newDirectoryStream(name,LinkOption.NOFOLLOW_LINKS)){
                    clearChildren(child,depth+1,budget);
                }
                dir.deleteDirectory(name);
            }else dir.deleteFile(name);
        }
    }
}
