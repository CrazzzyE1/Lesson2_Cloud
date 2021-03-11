package nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class NioTelnetServer
{
    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    public static final String LS_COMMAND = "\tls          view all files from current directory\n\r";
    public static final String MKDIR_COMMAND = "\tmkdir       view all files from current directory\n\r";

    public NioTelnetServer() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open(); // открыли
        server.bind(new InetSocketAddress(1234));
        server.configureBlocking(false); // ВАЖНО
        Selector selector = Selector.open();
        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started");
        while (server.isOpen()) {
            selector.select();
            var selectionKeys = selector.selectedKeys();
            var iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                var key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, selector);
                }
                iterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        int readBytes = channel.read(buffer);
        if (readBytes < 0) {
            channel.close();
            return;
        } else if (readBytes == 0) {
            return;
        }

        buffer.flip();
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        buffer.clear();

        // touch (имя файла) - создание файла
        // mkdir (имя директории) - создание директории
        // cd (path) - перемещение по дереву папок
        // rm (имя файла или папки) - удаление объекта
        // copy (src, target) - копирование файла
        // cat (имя файла) - вывод в консоль содержимого

        if (key.isValid()) {
            String command = sb.toString()
                    .replace("\n", "")
                    .replace("\r", "");
            if ("--help".equals(command)) {
                sendMessage(LS_COMMAND, selector);
                sendMessage(MKDIR_COMMAND, selector);
            } else if ("ls".equals(command)) {
                sendMessage(getFilesList().concat("\n\r"), selector);
            } else if ("exit".equals(command)) {
                System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
                channel.close();
                return;
            }
        }
        sendName(channel);
    }

    private void sendName(SocketChannel channel) throws IOException {
        channel.write(
                ByteBuffer.wrap(channel
                        .getRemoteAddress().toString()
                        .concat(">: ")
                        .getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    private String getFilesList() {
        return String.join("\t", new File("server").list());
    }

    private void sendMessage(String message, Selector selector) throws IOException {
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                ((SocketChannel) key.channel())
                        .write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client accepted. IP: " + channel.getRemoteAddress());
        channel.register(selector, SelectionKey.OP_READ, "some attach");
        channel.write(ByteBuffer.wrap("Hello user!\n\r".getBytes(StandardCharsets.UTF_8)));
        channel.write(ByteBuffer.wrap("Enter --help for support info\n\r".getBytes(StandardCharsets.UTF_8)));
        sendName(channel);
    }

    public static void main(String[] args) throws IOException {
        new NioTelnetServer();
    }
}