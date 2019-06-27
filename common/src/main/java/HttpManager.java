import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.CHUNKED;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpManager {
    private static HashMap<String, String> headers = new HashMap<>();
    private static Gson gson = new Gson();

//    Chunked
    public static void sendChunkedFile(Const.Action action, String relativePath, Path file, Channel channel) {
        HttpMessage msg;
        switch (action) {
            case DOWNLOAD:
                msg = new DefaultHttpResponse(HTTP_1_1, OK);
                break;
            case UPLOAD:
            default:
                msg = new DefaultHttpRequest(HTTP_1_1, HttpMethod.POST, Const.host);
        }
        msg.headers().set(TRANSFER_ENCODING, CHUNKED);
        msg.headers().add(CONTENT_TYPE, action);
        msg.headers().add(CONTENT_DISPOSITION, merge(relativePath, file.getFileName().toString()));

        RandomAccessFile raf;

        try {
            raf = new RandomAccessFile(file.toString(), "r");
            long fileLength = raf.length();
            HttpUtil.setContentLength(msg, fileLength);
            channel.write(msg);
            channel.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void receiveChunkedFile(HttpContent content, String path, boolean first) {
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(path, !first))) {
            byte[] bytes = new byte[content.content().capacity()];
            content.content().getBytes(0, bytes);
            out.write(bytes);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
//***

//    Request
    public static HttpRequest getRequest(Request obj) {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, HttpMethod.POST, Const.host, wrappedBuffer(gson.toJson(obj).getBytes(CharsetUtil.UTF_8)));
        request.headers().add(HttpHeaderNames.HOST, Const.host);
        request.headers().add(CONTENT_TYPE, obj.getAction());
        request.headers().set(CONTENT_LENGTH, request.content().readableBytes());
    return request;
}

    public static HttpRequest acceptHttpRequest() {
        String msg = "OK";
        FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.POST, Const.host, wrappedBuffer(msg.getBytes(CharsetUtil.UTF_8)));
        request.headers().add(HOST, Const.host);
        request.headers().add(ACCEPT, OK);
        request.headers().add(CONTENT_TYPE, Const.Action.NONE);
        request.headers().set(CONTENT_LENGTH, request.content().readableBytes());
        return request;
    }

    public static Request getRequest(HttpContent content) {
        return gson.fromJson(content.content().toString(CharsetUtil.UTF_8), Request.class);
    }
//***

//    Response
    private static HttpResponse getResponse(Const.Action action, Object obj) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, wrappedBuffer(gson.toJson(obj).getBytes(CharsetUtil.UTF_8)));
        response.headers().add(CONTENT_TYPE, action);
        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }

    public static AbstractMessage getMessage(HttpContent content, Class clazz) {
        return gson.fromJson(content.content().toString(CharsetUtil.UTF_8), (Type) clazz);
    }

    public static HttpResponse getListResponse(String path, List<MyFileDescriptor> list) {
        return getResponse(Const.Action.LIST, new FolderMessage(path, list));
    }

    public static HttpResponse getErrorResponse(Exception exception, Const.AlertText alertText, String file) {
        return getResponse(Const.Action.ERROR, new ErrorMessage(exception, alertText, file));
    }
//***

//    Headers
    public static void getHeaders(HttpObject httpObject) {
        headers.clear();
        if (httpObject instanceof HttpMessage) {
            HttpMessage obj = (HttpMessage) httpObject;
            if (!obj.headers().isEmpty()) {
                for (String name: obj.headers().names()) {
                    for (String value: obj.headers().getAll(name)) {
                        headers.put(name, value);
                    }
                }
            }
        }
    }

    public static String getHeadersValue(AsciiString name) {
        return headers.get(name.toString());
    }
//***

//    Копирование
    public static void copy(Const.Action action, Path source, Channel channel) throws IOException {
        List<Path> list = new ArrayList<>();
        MyFileVisitor fileVisitor = new MyFileVisitor(action, args -> list.add((Path) args[0]));
        Files.walkFileTree(source, fileVisitor);
        for (Path current: list) {
            String path = getRelativePath(source, current);
            if (Files.isDirectory(current)) {
                Request request = new Request(Const.Action.CREATE, merge(path, current.getFileName().toString()));
                switch (action) {
                    case DOWNLOAD:
                        channel.write(getResponse(request.getAction(), request));
                        break;
                    case UPLOAD:
                        channel.write(getRequest(request));
                        break;
                }
            } else {
                sendChunkedFile(action, path, current, channel);
            }
        }
    }

    private static String getRelativePath(Path source, Path path) {
        if (source.equals(path)) { return ""; }
        int startIndex = source.toString().lastIndexOf(File.separator) + 1;
        return path.toString().substring(startIndex, path.toString().lastIndexOf(File.separator));
    }
//***

//    Авторизация
public static FullHttpRequest getAuthRequest(String login, String password) {
    FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.POST, Const.host);
    ByteBuf encodedAuthBuffer = Base64.encode(Unpooled.copiedBuffer(Charset.forName("UTF-8").encode(login + ":" + password)));
    request.headers().add(HOST, Const.host);
    request.headers().add(AUTHORIZATION, encodedAuthBuffer.toString(CharsetUtil.UTF_8));
    request.headers().add(CONTENT_TYPE, Const.Action.AUTHORIZATION);
    request.headers().set(CONTENT_LENGTH, request.content().readableBytes());
    return request;
}

    public static FullHttpRequest getDisconnectRequest() {
        FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, Const.host);
        request.headers().add(HOST, Const.host);
        request.headers().add(AUTHORIZATION, CLOSE);
        request.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        request.headers().add(CONTENT_TYPE, Const.Action.AUTHORIZATION);
        request.headers().set(CONTENT_LENGTH, request.content().readableBytes());
        return request;
    }

    public static FullHttpResponse getAuthResponse(String nick) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, wrappedBuffer(nick.getBytes(CharsetUtil.UTF_8)));
        response.headers().add(AUTHORIZATION, OK);
        response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        response.headers().add(CONTENT_TYPE, Const.Action.AUTHORIZATION);
        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }

    public static String getLoginPassword() {
        if (!isAuthRequest()) {
            return null;
        }
        return Base64
                .decode(Unpooled.copiedBuffer(headers.get(AUTHORIZATION.toString()).getBytes(CharsetUtil.UTF_8)))
                .toString(CharsetUtil.UTF_8);
    }

    public static String getLogin() { return getLoginPassword().split(":")[0]; }
    public static String getPassword() { return getLoginPassword().split(":")[1]; }

    public static Boolean isAuthRequest() {
        return headers.get(AUTHORIZATION.toString()) != null;
    }
    public static Boolean isAuthMessage() {
        return headers.get(CONTENT_TYPE.toString()).equals(Const.Action.AUTHORIZATION.toString());
    }
//***
    public static Boolean isEmptyMessage() {
        return headers.get(CONTENT_TYPE.toString()).equals(Const.Action.NONE.toString());
    }

    private static String merge(String path, String file) {
        return String.join(path.isEmpty() ? "" : File.separator, path, file);
    }
}
