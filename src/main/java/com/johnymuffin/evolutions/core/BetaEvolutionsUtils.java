package com.johnymuffin.evolutions.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/*
 * A utility class (wrapper maybe?) for communicating with Beta Evolutions nodes
 *
 * @author RhysB
 * @version 1.0.3
 * @website https://evolutions.johnymuffin.com/
 *
 * This class has a license :)
 * ------------------------------------------------------------------------------
 * MIT License
 * Copyright (c) 2020 Rhys B
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ------------------------------------------------------------------------------
 *
 */

public class BetaEvolutionsUtils {
	private final boolean debug;

	public BetaEvolutionsUtils(boolean debug) {
		this.debug = debug;
	}

	private static final String[] beServers = {"https://auth1.evolutions.johnymuffin.com", "https://auth2.evolutions.johnymuffin.com"};

	//Core Methods - Start

	/**
	 * Attempt to authenticate the user with Evolution nodes
	 * !!!!This class is blocking
	 *
	 * @return VerificationResults class which contains successful/failed nodes
	 */
	public VerificationResults authenticateUser(String username, String sessionID) {
		//Fetch IP address for V2 and above support
		String ip = getExternalIP();
		if (ip == null) {
			log("Can't authenticate with any nodes, can't fetch external IP address. Your internet is probably offline!");
			return new VerificationResults(0, 0, beServers.length);
		}
		VerificationResults verificationResults = new VerificationResults();
		//Iterate through all nodes while verifying
		for (String node : beServers) {
			Boolean result = authenticateWithBetaEvolutions(username, node, sessionID, ip);
			if (result == null) {
				verificationResults.setErrored(verificationResults.getErrored() + 1);
			} else if (result) {
				verificationResults.setSuccessful(verificationResults.getSuccessful() + 1);
			} else {
				verificationResults.setFailed(verificationResults.getFailed() + 1);
			}
		}
		return verificationResults;

	}

	//Client Methods - Start
	private Boolean authenticateWithMojang(String username, String sessionID, String serverID) {
		try {
			String authURL = "https://session.minecraft.net/game/joinserver.jsp?user=" + encodeString(username) + "&sessionId=" + encodeString(sessionID) + "&serverId=" + encodeString(serverID);
			URL url = new URL(authURL);
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(url.openStream()));
			String response = bufferedReader.readLine();
			bufferedReader.close();
            return response.equalsIgnoreCase("ok");
		} catch (Exception e) {
			if (debug) {
				log("An error occurred contacting Mojang.");
				e.printStackTrace();
			}
		}
		return null;
	}

	private Boolean authenticateWithBetaEvolutions(String username, String node, String sessionToken, String ip) {

			//State 1 - Contact the node with username and method type
			String stage1URL = node + "/user/getServerID?username=" + encodeString(username) + "&userip=" + ip;
			JSONObject stage1Object = getJSONFromURL(stage1URL, node);
			if (stage1Object == null) {
				log("Authentication with node: " + node + " has failed as JSON can't be fetched.");
				return null;
			}
			if (verifyJSONArguments(stage1Object, "userIP", "error", "serverID", "username")) {
				log("Malformed response from: " + node);
				return null;
			}
			String serverID = stage1Object.getString("serverID");
			//Stage 2 - Contact Mojang to authenticate
			Boolean mojangAuthentication = authenticateWithMojang(username, sessionToken, serverID);
			if (mojangAuthentication == null) {
				log("Authentication with node: " + node + " has failed due to auth failure with Mojang.");
				return null;
			} else if (!mojangAuthentication) {
				log("Authentication with node: " + node + " has failed. Token is probably incorrect, or user is cracked!");
				return false;
			}
			//Stage 3 - Contact node to confirm auth
			String stage3URL = node + "/user/successfulAuth?username=" + encodeString(username) + "&serverid=" + encodeString(serverID) + "&userip=" + encodeString(ip);
			JSONObject stage3Object = getJSONFromURL(stage3URL, node);
			if (stage3Object == null) {
				log("Authentication with node: " + node + " has failed as JSON can't be fetched.");
				return null;
			}
			if (verifyJSONArguments(stage3Object, "result")) {
				log("Malformed response from: " + node);
				return null;
			}
			return stage3Object.getBoolean("result");

	}

	private String getExternalIP() {
		String ip = getIPFromAmazon();
		if (ip == null) {
			ip = getIPFromICanHazIP();
		}
		return ip;
	}


	private String getIPFromAmazon() {
		try {
			URL myIP = new URL("https://checkip.amazonaws.com");
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(myIP.openStream()));
			return bufferedReader.readLine();

		} catch (Exception e) {
			log("Failed to get IP from Amazon, your internet is probably down.");
			if (debug) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private String getIPFromICanHazIP() {
		try {
			URL myIP = new URL("https://icanhazip.com/");
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(myIP.openStream()));
			return bufferedReader.readLine();

		} catch (Exception e) {
			log("Failed to get IP from ICanHazIP, your internet is probably down.");
			if (debug) {
				e.printStackTrace();
			}
		}
		return null;
	}


	//Client Methods - End

	//Utils - Start

	//Method readJsonFromUrl and readAll licensed under CC BY-SA 2.5 (https://stackoverflow.com/help/licensing)
	//Credit: https://stackoverflow.com/a/4308662

	private static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        return readJsonFromUrlWithTimeout(url);
	}

	private static JSONObject readJsonFromUrlWithTimeout(String url) throws IOException, JSONException {
		URL myURL = new URL(url);
		HttpURLConnection connection = (HttpURLConnection) myURL.openConnection();
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(5000);
		connection.setRequestMethod("GET");
		connection.connect();
        try (InputStream is = connection.getInputStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String jsonText = readAll(rd);
            return new JSONObject(jsonText);
        }
	}

	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1)
			sb.append((char) cp);
		return sb.toString();
	}


	private JSONObject getJSONFromURL(String url, String node) {
		try {
            return readJsonFromUrl(url);
		} catch (Exception e) {
			if (debug) {
				log("An error occurred fetching JSON from: " + node);
				e.printStackTrace();
			}
		}
		return null;
	}


	private void log(String info) {
		if (debug) {
			System.out.println("[Beta Evolutions] " + info);
		}
	}

	private String encodeString(String string) {
		try {
			return URLEncoder.encode(string, StandardCharsets.UTF_8.toString());
		} catch (Exception e) {
			log("An error occurred encoding a string, this really shouldn't happen on modern JVMs.");
			e.printStackTrace();
		}
		return null;
	}


	private boolean verifyJSONArguments(JSONObject jsonObject, String... arguments) {
		for (String s : arguments) {
			if (!jsonObject.has(s)) return true;
		}
		return false;
	}


	//Utils - End


	public static class VerificationResults {
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
			return successful;
		}

		public void setSuccessful(int successful) {
			this.successful = successful;
		}

		public int getFailed() {
			return failed;
		}

		public void setFailed(int failed) {
			this.failed = failed;
		}

		public int getErrored() {
			return errored;
		}

		public void setErrored(int errored) {
			this.errored = errored;
		}

		public int getTotal() {
			return (errored + successful + failed);
		}

	}


}
