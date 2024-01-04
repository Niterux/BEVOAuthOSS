package com.legacyminecraft.authentication;

import com.johnymuffin.evolutions.beta.simplejson.JSONObject;
import com.johnymuffin.evolutions.beta.simplejson.parser.JSONParser;
import com.johnymuffin.evolutions.beta.simplejson.parser.ParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class BetaEvolutionsUtils {
   private boolean debug;
   private static HashMap<String, BetaEvolutionsUtils.BEVersion> beServers = new HashMap<>();

   public BetaEvolutionsUtils() {
      this.debug = false;
   }

   public BetaEvolutionsUtils(boolean debug) {
      this.debug = debug;
   }

   public BetaEvolutionsUtils.VerificationResults authenticateUser(String username, String sessionID) {
      String ip = this.getExternalIP();
      if (ip == null) {
         this.log("Can't authenticate with any nodes, can't fetch external IP address. Your internet is probably offline!");
         return new BetaEvolutionsUtils.VerificationResults(0, 0, beServers.size());
      } else {
         BetaEvolutionsUtils.VerificationResults verificationResults = new BetaEvolutionsUtils.VerificationResults();

         for(String node : beServers.keySet()) {
            Boolean result = this.authenticateWithBetaEvolutions(username, node, beServers.get(node), sessionID, ip);
            if (result == null) {
               verificationResults.setErrored(verificationResults.getErrored() + 1);
            } else if (result) {
               verificationResults.setSuccessful(verificationResults.getSuccessful() + 1);
            } else if (!result) {
               verificationResults.setFailed(verificationResults.getFailed() + 1);
            }
         }

         return verificationResults;
      }
   }

   public BetaEvolutionsUtils.VerificationResults verifyUser(String username, String userIP) {
      BetaEvolutionsUtils.VerificationResults verificationResults = new BetaEvolutionsUtils.VerificationResults();

      for(String node : beServers.keySet()) {
         Boolean result = this.verifyUserWithNode(username, userIP, node, beServers.get(node));
         if (result == null) {
            verificationResults.setErrored(verificationResults.getErrored() + 1);
         } else if (result) {
            verificationResults.setSuccessful(verificationResults.getSuccessful() + 1);
         } else if (!result) {
            verificationResults.setFailed(verificationResults.getFailed() + 1);
         }
      }

      return verificationResults;
   }

   private Boolean verifyUserWithNode(String username, String userIP, String node, BetaEvolutionsUtils.BEVersion beVersion) {
      if (beVersion == BetaEvolutionsUtils.BEVersion.V1) {
         String stage1URL = node + "/serverAuth.php?method=1&username=" + this.encodeString(username) + "&userip=" + this.encodeString(userIP);
         JSONObject stage1Object = this.getJSONFromURL(stage1URL);
         if (stage1Object == null) {
            this.log("Authentication with node: " + node + " has failed to respond when queried.");
            return null;
         } else if (!this.verifyJSONArguments(stage1Object, "result", "verified")) {
            this.log("Malformed response from: " + node + " using version " + beVersion);
            return null;
         } else {
            return Boolean.valueOf(String.valueOf(stage1Object.get("verified")));
         }
      } else if (beVersion == BetaEvolutionsUtils.BEVersion.V2_PLAINTEXT) {
         String stage1URL = node + "/server/getVerification?username=" + this.encodeString(username) + "&userip=" + this.encodeString(userIP);
         JSONObject stage1Object = this.getJSONFromURL(stage1URL);
         if (stage1Object == null) {
            this.log("Authentication with node: " + node + " has failed to respond when queried.");
            return null;
         } else if (!this.verifyJSONArguments(stage1Object, "verified", "error")) {
            this.log("Malformed response from: " + node + " using version " + beVersion);
            return null;
         } else {
            return Boolean.valueOf(String.valueOf(stage1Object.get("verified")));
         }
      } else {
         return null;
      }
   }

   private Boolean authenticateWithMojang(String username, String sessionID, String serverID) {
      try {
         String authURL = "http://session.minecraft.net/game/joinserver.jsp?user="
            + this.encodeString(username)
            + "&sessionId="
            + this.encodeString(sessionID)
            + "&serverId="
            + this.encodeString(serverID);
         URL url = new URL(authURL);
         BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(url.openStream()));
         String response = bufferedReader.readLine();
         bufferedReader.close();
         return response.equalsIgnoreCase("ok") ? true : false;
      } catch (Exception var8) {
         if (this.debug) {
            this.log("An error occurred contacting Mojang.");
            var8.printStackTrace();
         }

         return null;
      }
   }

   private Boolean authenticateWithBetaEvolutions(String username, String node, BetaEvolutionsUtils.BEVersion beVersion, String sessionToken, String ip) {
      if (beVersion == BetaEvolutionsUtils.BEVersion.V1) {
         String stage1URL = node + "/userAuth.php?method=1&username=" + this.encodeString(username);
         JSONObject stage1Object = this.getJSONFromURL(stage1URL);
         if (stage1Object == null) {
            this.log("Authentication with node: " + node + " has failed as JSON can't be fetched.");
            return null;
         } else if (!this.verifyJSONArguments(stage1Object, "result", "username", "userip", "serverId")) {
            this.log("Malformed response from: " + node + " using version " + beVersion);
            return null;
         } else {
            String serverID = String.valueOf(stage1Object.get("serverId"));
            Boolean mojangAuthentication = this.authenticateWithMojang(username, sessionToken, serverID);
            if (mojangAuthentication == null) {
               this.log("Authentication with node: " + node + " has failed due to auth failure with Mojang.");
               return null;
            } else if (!mojangAuthentication) {
               this.log("Authentication with node: " + node + " has failed. Token is probably incorrect, or user is cracked!");
               return false;
            } else {
               String stage3URL = node + "/userAuth.php?method=2&username=" + this.encodeString(username) + "&serverId=" + this.encodeString(serverID);
               JSONObject stage3Object = this.getJSONFromURL(stage3URL);
               if (stage3Object == null) {
                  this.log("Authentication with node: " + node + " has failed as JSON can't be fetched.");
                  return null;
               } else if (!this.verifyJSONArguments(stage3Object, "result")) {
                  this.log("Malformed response from: " + node + " using version " + beVersion);
                  return null;
               } else {
                  Boolean result = Boolean.valueOf(String.valueOf(stage3Object.get("result")));
                  this.log("Node: " + node + " has returned the result: " + result);
                  return result;
               }
            }
         }
      } else if (beVersion == BetaEvolutionsUtils.BEVersion.V2_PLAINTEXT) {
         String stage1URL = node + "/user/getServerID?username=" + this.encodeString(username) + "&userip=" + ip;
         JSONObject stage1Object = this.getJSONFromURL(stage1URL);
         if (stage1Object == null) {
            this.log("Authentication with node: " + node + " has failed as JSON can't be fetched.");
            return null;
         } else if (!this.verifyJSONArguments(stage1Object, "userIP", "error", "serverID", "username")) {
            this.log("Malformed response from: " + node + " using version " + beVersion);
            return null;
         } else {
            String serverID = String.valueOf(stage1Object.get("serverID"));
            Boolean mojangAuthentication = this.authenticateWithMojang(username, sessionToken, serverID);
            if (mojangAuthentication == null) {
               this.log("Authentication with node: " + node + " has failed due to auth failure with Mojang.");
               return null;
            } else if (!mojangAuthentication) {
               this.log("Authentication with node: " + node + " has failed. Token is probably incorrect, or user is cracked!");
               return false;
            } else {
               String stage3URL = node
                  + "/user/successfulAuth?username="
                  + this.encodeString(username)
                  + "&serverid="
                  + this.encodeString(serverID)
                  + "&userip="
                  + this.encodeString(ip);
               JSONObject stage3Object = this.getJSONFromURL(stage3URL);
               if (stage3Object == null) {
                  this.log("Authentication with node: " + node + " has failed as JSON can't be fetched.");
                  return null;
               } else if (!this.verifyJSONArguments(stage3Object, "result")) {
                  this.log("Malformed response from: " + node + " using version " + beVersion);
                  return null;
               } else {
                  Boolean result = Boolean.valueOf(String.valueOf(stage3Object.get("result")));
                  this.log("Node: " + node + " has returned the result: " + result);
                  return result;
               }
            }
         }
      } else {
         return null;
      }
   }

   private String getExternalIP() {
      String ip = this.getIPFromAmazon();
      if (ip == null) {
         ip = this.getIPFromWhatIsMyIpAddress();
      }

      return ip;
   }

   private String getIPFromAmazon() {
      try {
         URL myIP = new URL("http://checkip.amazonaws.com");
         BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(myIP.openStream()));
         return bufferedReader.readLine();
      } catch (Exception var3) {
         this.log("Failed to get IP from Amazon, your internet is probably down.");
         if (this.debug) {
            var3.printStackTrace();
         }

         return null;
      }
   }

   private String getIPFromWhatIsMyIpAddress() {
      try {
         URL myIP = new URL("https://ipv4bot.whatismyipaddress.com/");
         BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(myIP.openStream()));
         return bufferedReader.readLine();
      } catch (Exception var3) {
         this.log("Failed to get IP from WhatIsMyIpAddress, your internet is probably down.");
         if (this.debug) {
            var3.printStackTrace();
         }

         return null;
      }
   }

   private static JSONObject readJsonFromUrl(String url) throws IOException, ParseException, UnknownHostException {
      InputStream is = new URL(url).openStream();

      JSONObject var6;
      try {
         BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
         String jsonText = readAll(rd);
         JSONParser jsonParser = new JSONParser();
         JSONObject json = (JSONObject)jsonParser.parse(jsonText);
         var6 = json;
      } finally {
         is.close();
      }

      return var6;
   }

   private static String readAll(Reader rd) throws IOException {
      StringBuilder sb = new StringBuilder();

      int cp;
      while((cp = rd.read()) != -1) {
         sb.append((char)cp);
      }

      return sb.toString();
   }

   private JSONObject getJSONFromURL(String url) {
      try {
         return readJsonFromUrl(url);
      } catch (UnknownHostException var3) {
         this.log(url + " is offline, or your internet is offline.");
      } catch (Exception var4) {
         if (this.debug) {
            this.log("An error occurred fetching JSON from: " + url);
            var4.printStackTrace();
         }
      }

      return null;
   }

   private void log(String info) {
      if (this.debug) {
         System.out.println("[Beta Evolutions] " + info);
      }
   }

   private String encodeString(String string) {
      try {
         return URLEncoder.encode(string, StandardCharsets.UTF_8.toString());
      } catch (Exception var3) {
         this.log("An error occurred encoding a string, this really shouldn't happen on modern JVMs.");
         var3.printStackTrace();
         return null;
      }
   }

   private boolean verifyJSONArguments(JSONObject jsonObject, String... arguments) {
      for(String s : arguments) {
         if (!jsonObject.containsKey(s)) {
            return false;
         }
      }

      return true;
   }

   static {
      beServers.put("https://auth.johnymuffin.com", BetaEvolutionsUtils.BEVersion.V1);
      beServers.put("https://auth1.evolutions.johnymuffin.com", BetaEvolutionsUtils.BEVersion.V2_PLAINTEXT);
      beServers.put("https://auth2.evolutions.johnymuffin.com", BetaEvolutionsUtils.BEVersion.V2_PLAINTEXT);
      beServers.put("https://auth3.evolutions.johnymuffin.com", BetaEvolutionsUtils.BEVersion.V2_PLAINTEXT);
   }

   public static enum BEVersion {
      V1,
      V2_PLAINTEXT;
   }

   public class VerificationResults {
      private int successful = 0;
      private int failed = 0;
      private int errored = 0;

      public VerificationResults() {
      }

      public VerificationResults(int successful, int failed, int errored) {
         this.successful = successful;
         this.failed = failed;
         this.errored = errored;
      }

      public int getSuccessful() {
         return this.successful;
      }

      public void setSuccessful(int successful) {
         this.successful = successful;
      }

      public int getFailed() {
         return this.failed;
      }

      public void setFailed(int failed) {
         this.failed = failed;
      }

      public int getErrored() {
         return this.errored;
      }

      public void setErrored(int errored) {
         this.errored = errored;
      }

      public int getTotal() {
         return this.errored + this.successful + this.failed;
      }
   }
}
