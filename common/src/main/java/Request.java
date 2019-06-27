import java.util.Arrays;

public class Request extends AbstractMessage {
    private Const.Action action;
    private String[] arg;

    public Request(Const.Action action, String ...arg) {
        this.action = action;
        this.arg = arg;
    }

    public Const.Action getAction() { return action; }
    public String getArg(int index) { return arg[index]; }

    @Override
    public String toString() {
        return "Request{" +
                "action=" + action +
                ", arg=" + Arrays.toString(arg) +
                '}';
    }
}
//AUTHORIZATION: login, password
//LIST:          path (empty - переход в вышестоящий каталог)
//CREATE:
//DOWNLOAD:
//UPLOAD:
//REMOVE:        name - при ручном создании,  path - при обходе FileVisitor
