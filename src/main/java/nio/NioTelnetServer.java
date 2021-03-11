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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class NioTelnetServer {
    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    public static final String LS_COMMAND =    "\tls          view all files from current directory\n\r";
    public static final String MKDIR_COMMAND = "\tmkdir       mkdir (имя директории) - создание директории\n\r";
    public static final String TOUCH =         "\ttouch       touch (имя файла) - создание файла\n\r";
    public static final String CD =            "\tcd          cd (path) - перемещение по дереву папок\n\r";
    public static final String RM =            "\trm          rm (имя файла или папки) - удаление объекта\n\r";
    public static final String COPY =          "\tcopy        copy (src, target) - копирование файла\n\r";
    public static final String CAT =           "\tcat         cat (имя файла) - вывод в консоль содержимого\n\r";
    public static final String EXIT =          "\texit        exxxxiiiiittttt\n\r";
    private Path path = Path.of("server");
    private Path pathTarget = Path.of("server");


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
        String[] strings = sb.toString()
                .replace("\n", "")
                .replace("\r", "")
                .split(" ");

        // + touch (имя файла) - создание файла
        // + mkdir (имя директории) - создание директории
        // + cd (path) - перемещение по дереву папок
        // + rm (имя файла или папки) - удаление объекта
        // + copy (src, target) - копирование файла
        // + cat (имя файла) - вывод в консоль содержимого

        if (key.isValid()) {
            String command = strings[0];
            if ("--help".equals(command)) {
                sendMessage(LS_COMMAND, selector);
                sendMessage(MKDIR_COMMAND, selector);
                sendMessage(TOUCH, selector);
                sendMessage(CD, selector);
                sendMessage(RM, selector);
                sendMessage(COPY, selector);
                sendMessage(CAT, selector);
                sendMessage(EXIT, selector);
            } else if ("ls".equals(command)) {
                sendMessage(getFilesList(path).concat("\n\r"), selector);
            } else if ("exit".equals(command)) {
                System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
                channel.close();
                return;
            } else if ("touch".equals(command)) {
                if (!Files.exists(Path.of(path + File.separator + strings[1]))) {
                    Files.createFile(Path.of(path + File.separator + strings[1]));
                }
            } else if ("cat".equals(command)) {
                if(Files.exists(Path.of(path + File.separator + strings[1]))){
                    Files.readAllLines(Path.of(path + File.separator + strings[1])).forEach(System.out::println);
                } else {
                    sendMessage("File not found\n\r" , selector);
                }
            } else if ("cd".equals(command)) {
                if(Files.exists(Path.of(strings[1]))) {
                    path = Path.of(strings[1]);
                    sendMessage("CD to: " + path.toString() + "\n\r", selector);
                } else  {
                    sendMessage("Wrong path\n\r", selector);
                }
            } else if ("mkdir".equals(command)) {
                Path dir = Path.of(String.valueOf(path), strings[1]);
                Files.createDirectories(dir);
            } else if ("rm".equals(command)) {
                if(Files.exists(Path.of(String.valueOf(path), File.separator + strings[1]))){
                    Files.delete(Path.of(String.valueOf(path), File.separator + strings[1]));
                } else {
                    sendMessage("File not found\n\r", selector);
                }
            } else if ("copy".equals(command)) {
                if(Files.exists(Path.of(path + File.separator + strings[1])) && Files.exists(Path.of(strings[2]))){
                    Files.copy(Path.of(path + File.separator + strings[1]), Path.of(strings[2]).resolve(strings[1]), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    sendMessage("File or Directory not found\n\r", selector);
                }

            } else {
                sendMessage("Unknown command".concat("\n\r"), selector);
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

    private String getFilesList(Path path) {
        return String.join("\t", new File(path.toString()).list());
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