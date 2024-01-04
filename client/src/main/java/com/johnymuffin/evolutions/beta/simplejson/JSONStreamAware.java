package com.johnymuffin.evolutions.beta.simplejson;

import java.io.IOException;
import java.io.Writer;

public interface JSONStreamAware {
   void writeJSONString(Writer var1) throws IOException;
}
