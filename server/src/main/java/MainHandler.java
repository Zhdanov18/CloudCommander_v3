import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT;

public class MainHandler extends SimpleChannelInboundHandler<HttpObject> {
    private boolean authorized;
    private boolean firstContent = true;
    private String nick;
    private String path;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg == null) {
            return;
        }
        if (msg instanceof HttpRequest || msg instanceof HttpResponse) {
            HttpManager.getHeaders(msg);
        }
        if (!(msg instanceof HttpContent)) {
            return;
        }
        HttpContent content = (HttpContent) msg;
        if (!authorized) {
            if (HttpManager.isAuthMessage()) {
                AuthService authService = new SQLiteAuthService();
                nick = authService.getName(HttpManager.getLogin(), HttpManager.getPassword());
                authService.disconnect();

                File clientRoot = new File(getClientRoot());
                if (!clientRoot.exists()) {
                    clientRoot.mkdir();
                }
                this.path = getClientRoot();

                if (nick != null) {
                    authorized = true;
                    ctx.write(HttpManager.getAuthResponse(nick));
                }
            }
            return;
        } else if (HttpManager.isAuthMessage()) {
            authorized = false;
            return;
        }

        switch (HttpManager.getHeadersValue(CONTENT_TYPE)) {
            case "UPLOAD":
                if (content instanceof LastHttpContent) {
                    firstContent = true;
                    ctx.write(HttpManager.getListResponse(path, getServerFileList()));
                } else {
                    String filename = String.join(File.separator, path, HttpManager.getHeadersValue(CONTENT_DISPOSITION));
                    HttpManager.receiveChunkedFile(content, filename, firstContent);
                    firstContent = false;
                }
                return;
        }

        if (msg.equals(EMPTY_LAST_CONTENT) || HttpManager.isEmptyMessage()) {
            return;
        }

        Request request = HttpManager.getRequest(content);
        Path file;
        switch (HttpManager.getHeadersValue(CONTENT_TYPE)) {
            case "CREATE":
                file = Paths.get(path, File.separator, request.getArg(0));
                if (FileManager.createDirectory(file, null)) {
                    ctx.write(HttpManager.getListResponse(path, getServerFileList()));
                } else {
                    ctx.write(HttpManager.getErrorResponse(null, Const.AlertText.CREATE_DIR_ERR, file.toString()));
                }
                break;
            case "DOWNLOAD":
                file = Paths.get(path, request.getArg(0));
                try {
                    HttpManager.copy(Const.Action.DOWNLOAD, file, ctx.channel());
                } catch (IOException e) {
                    ctx.write(HttpManager.getErrorResponse(e, Const.AlertText.COPY_ERR, file.toString()));
                }
                break;
            case "LIST":
                if (!(request.getArg(0).isEmpty() && path.equals(getClientRoot()))) {
                    String result = FileManager.changeDirectory(request.getArg(0), path);
                    if (result != null) {
                        path = result;
                    }
                }
                ctx.write(HttpManager.getListResponse(path, getServerFileList()));
                break;
            case "REMOVE":
                file = Paths.get(path, File.separator, request.getArg(0));
                try {
                    FileManager.remove(file);
                    ctx.write(HttpManager.getListResponse(path, getServerFileList()));
                } catch (IOException e) {
                    ctx.write(HttpManager.getErrorResponse(e, Const.AlertText.DELETE_ERR, file.toString()));
                }
                break;
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    public String getClientRoot() {
        return Const.storageRoot + this.nick + File.separator;
    }

    public List<MyFileDescriptor> getServerFileList() {
        return FileManager.getFileList(path, getClientRoot());
    }

}
