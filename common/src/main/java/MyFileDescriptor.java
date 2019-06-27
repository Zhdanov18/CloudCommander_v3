import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class MyFileDescriptor implements Serializable {
    private String  name;
    private long    size;

    public MyFileDescriptor(Path path) {
        this.name = path.getFileName().toString().trim();
        if (Files.isRegularFile(path)) {
            try {
                this.size = Files.size(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public MyFileDescriptor(String name) {
        this.name = name;
        this.size = 0;
    }

    public String  getName() { return name; }
    public long    getSize() { return size; }

    public Boolean isDirectory() { return size == 0 && !isParent(); }
    public Boolean isParent()    { return name.contains(File.separator); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MyFileDescriptor that = (MyFileDescriptor) o;
        return getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), size);
    }

    @Override
    public String toString() {
        return "MyFileDescriptor{" +
                "name='" + name + '\'' +
                ", size=" + size +
                '}';
    }
}
