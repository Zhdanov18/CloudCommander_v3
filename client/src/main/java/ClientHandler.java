import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.netty.handler.codec.http.HttpHeaderNames.*;

public class ClientHandler extends SimpleChannelInboundHandler<HttpObject> {
    private MainController fx;
    private boolean firstContent = true;

    public ClientHandler(MainController mainController) {
        this.fx = mainController;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg == null) {
            return;
        }
        if (msg instanceof HttpRequest || msg instanceof HttpResponse) {
            HttpManager.getHeaders(msg);
        }
        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;
            switch (HttpManager.getHeadersValue(CONTENT_TYPE)) {
                case "AUTHORIZATION":
                    fx.setOnline(content.content().toString(CharsetUtil.UTF_8));
                    ctx.write(HttpManager.getRequest(new Request(Const.Action.LIST, "")));
                    break;
                case "DOWNLOAD":
                    if (content instanceof LastHttpContent) {
                        fx.refreshClientList();
                        firstContent = true;
                        ctx.write(HttpManager.acceptHttpRequest());
                    } else {
                        String filename = String.join(File.separator, fx.client.getPath(), HttpManager.getHeadersValue(CONTENT_DISPOSITION));
                        HttpManager.receiveChunkedFile(content, filename, firstContent);
                        firstContent = false;
                    }
                    break;
                case "CREATE":
                    Request request = HttpManager.getRequest(content);
                    Path path = Paths.get(fx.client.getPath(), File.separator, request.getArg(0));
                    if (FileManager.createDirectory(path, null) && !request.getArg(0).contains(File.separator)) {
                        fx.setDirection(Const.Direction.NONE);
                        fx.refreshClientList();
                    }
                    ctx.write(HttpManager.acceptHttpRequest());
                    break;
                case "ERROR":
                    ErrorMessage em = (ErrorMessage) HttpManager.getMessage(content, ErrorMessage.class);
                    fx.updateUI(() -> {
                        DialogManager.errorMsg(em.getAlertText(), fx.server.getPathForDisplay() + File.separator + em.getContentText(), em.getException());
                    });
                    break;
                case "LIST":
                    if (content instanceof DefaultLastHttpContent) {
                        FolderMessage fm = (FolderMessage) HttpManager.getMessage(content, FolderMessage.class);
                        fx.refreshServerList(fm);
                        fx.server.setPath(fm.getPath());
                    }
                    ctx.write(HttpManager.acceptHttpRequest());
                    break;
            }
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
}
