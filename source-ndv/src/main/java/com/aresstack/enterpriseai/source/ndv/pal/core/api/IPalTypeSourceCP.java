package com.aresstack.enterpriseai.source.ndv.pal.core.api;

import com.aresstack.enterpriseai.source.ndv.pal.core.impl.type.IPalType;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public interface IPalTypeSourceCP extends IPalType {
   void convert(String var1) throws UnsupportedEncodingException, IOException;
}
