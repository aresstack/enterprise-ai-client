package com.aresstack.enterpriseai.source.ndv.pal.core.api;

public interface ICompOpt {
   int getFlags();

   int getSourceLinelength();

   int getMaxprec();

   void setFlags(int var1);

   void resetFlags(int var1);

   void setMaxprec(int var1);
}
