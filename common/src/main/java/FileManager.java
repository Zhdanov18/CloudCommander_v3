import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FileManager {

    public static List<MyFileDescriptor> getFileList(String path, String root) {
        List<MyFileDescriptor> data = new ArrayList<>();
        File dir = new File(path);
        for (File f : dir.listFiles())
            data.add(new MyFileDescriptor(Paths.get(f.getAbsolutePath())));
        data.sort((o1, o2) -> {
            if (o1.isDirectory() && !o2.isDirectory()) {
                return -1;
            } else if (!o1.isDirectory() && o2.isDirectory()) {
                return 1;
            } else {
                return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
            }
        });
        if (!path.equals(root)) {
            data.add(0, new MyFileDescriptor(path));
        }
        return data;
    }

    public static boolean createDirectory(Path path, Exception exception) {
        boolean create = false;
        try {
            if (Files.exists(Files.createDirectory(path))) {
                create = true;
            }
        } catch (IOException e) {
            if (exception != null) exception = e;
        } finally {
            return create;
        }
    }

    public static String changeDirectory(String directory, String path) {
        String newPath;
        if (!directory.isEmpty()) {
            newPath = path + directory + File.separator;
        } else {
            newPath = path.substring(0, path.substring(0, path.length() - 2).lastIndexOf(File.separator)) + File.separator;
        }
        if (Files.exists(Paths.get(newPath))) {
            return newPath;
        }
        return null;
    }

    public static void remove(Path source) throws IOException {
        MyFileVisitor fileVisitor = new MyFileVisitor(Const.Action.REMOVE, null);
        Files.walkFileTree(source, fileVisitor);
    }
}
