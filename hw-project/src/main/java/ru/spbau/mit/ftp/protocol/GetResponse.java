package ru.spbau.mit.ftp.protocol;

import java.io.*;
import java.nio.channels.FileChannel;

//public class GetResponse extends Response {
//    FileInputStream fin;
//
//    GetResponse(File file) throws FileNotFoundException {
//        this.fin = new FileInputStream(file);
//    }
//
//    @Override
//    public void write(WritableByteChannel out) throws IOException {
//        checkForNonEmptyness();
//        FileChannel fileChannel = fin.getChannel();
//    }
//
//    @Override
//    public void read(ReadableByteChannel in) throws IOException {
//        checkForEmptyness();
//        int byteCount =
//    }
//
//    @Override
//    public String debugString() {
//        return null;
//    }
//}
