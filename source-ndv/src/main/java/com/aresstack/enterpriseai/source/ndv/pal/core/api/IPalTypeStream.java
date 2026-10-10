package com.aresstack.enterpriseai.source.ndv.pal.core.api;

import com.aresstack.enterpriseai.source.ndv.pal.core.impl.type.IPalType;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public interface IPalTypeStream extends IPalType {
   void convert(String var1, boolean var2) throws UnsupportedEncodingException, IOException;

   byte[] getStreamRecord();

   void setStreamRecord(byte[] var1);
}
