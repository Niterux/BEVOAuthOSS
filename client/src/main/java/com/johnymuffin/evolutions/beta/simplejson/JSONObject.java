package com.johnymuffin.evolutions.beta.simplejson;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class JSONObject extends HashMap implements Map, JSONAware, JSONStreamAware {
   private static final long serialVersionUID = -503443796854799292L;

   public JSONObject() {
   }

   public JSONObject(Map map) {
      super(map);
   }

   public static void writeJSONString(Map map, Writer out) throws IOException {
      if (map == null) {
         out.write("null");
      } else {
         boolean first = true;
         Iterator iter = map.entrySet().iterator();
         out.write(123);

         while(iter.hasNext()) {
            if (first) {
               first = false;
            } else {
               out.write(44);
            }

            Entry entry = (Entry)iter.next();
            out.write(34);
            out.write(escape(String.valueOf(entry.getKey())));
            out.write(34);
            out.write(58);
            JSONValue.writeJSONString(entry.getValue(), out);
         }

         out.write(125);
      }
   }

   @Override
   public void writeJSONString(Writer out) throws IOException {
      writeJSONString(this, out);
   }

   public static String toJSONString(Map map) {
      StringWriter writer = new StringWriter();

      try {
         writeJSONString(map, writer);
         return writer.toString();
      } catch (IOException var3) {
         throw new RuntimeException(var3);
      }
   }

   @Override
   public String toJSONString() {
      return toJSONString(this);
   }

   @Override
   public String toString() {
      return this.toJSONString();
   }

   public static String toString(String key, Object value) {
      StringBuffer sb = new StringBuffer();
      sb.append('"');
      if (key == null) {
         sb.append("null");
      } else {
         JSONValue.escape(key, sb);
      }

      sb.append('"').append(':');
      sb.append(JSONValue.toJSONString(value));
      return sb.toString();
   }

   public static String escape(String s) {
      return JSONValue.escape(s);
   }
}
