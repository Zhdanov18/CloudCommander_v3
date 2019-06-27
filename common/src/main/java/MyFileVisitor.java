import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class MyFileVisitor extends SimpleFileVisitor<Path> {

    private Const.Action action;
    private Callback execute;

    public MyFileVisitor(Const.Action action, Callback execute) {
        this.action  = action;
        this.execute = execute;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        switch (action) {
            case REMOVE:
                Files.deleteIfExists(file);
                break;
            case UPLOAD:
            case DOWNLOAD:
                execute.callback(file);
                break;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
    {
        switch (action) {
            case REMOVE:
                break;
            case UPLOAD:
            case DOWNLOAD:
                execute.callback(dir);
                break;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path path, IOException exc) throws IOException {
        switch (action) {
            case REMOVE:
                Files.deleteIfExists(path);
                break;
            case UPLOAD:
            case DOWNLOAD:
                break;
        }
        return FileVisitResult.CONTINUE;
    }
}
