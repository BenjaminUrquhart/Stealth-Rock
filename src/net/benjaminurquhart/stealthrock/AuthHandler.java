package net.benjaminurquhart.stealthrock;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.json.JSONObject;

import com.github.scribejava.core.model.OAuth2AccessToken;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.router.RouterNanoHTTPD.GeneralHandler;
import fi.iki.elonen.router.RouterNanoHTTPD.UriResource;

public class AuthHandler extends GeneralHandler {

	@SuppressWarnings("deprecation")
	public Response get(UriResource uri, Map<String, String> params, IHTTPSession session) {
		try {
			params = session.getParms();
			String state = params.get("state");
			String code = params.get("code");
			
			System.out.println(uri.getUri() + " " + params);
			try {
				UUID uuid = null;
				String id = null;
				String destination = null;
				boolean validState = state != null && code != null;
				if(validState) {
					String[] split = state.split("\\=");
					id = split[0];
					destination = split[1] + "/" + split[2];
					uuid = UUID.fromString(id);
					validState = Main.PENDING_AUTH.remove(id) != null;
				}
				if(validState) {
					try {
						OAuth2AccessToken token = Main.OAUTH_SERVICE.getAccessToken(code);
						JSONObject json = new JSONObject().put("token", token.getAccessToken())
														  .put("refresh", token.getRefreshToken())
														  .put("expiry", System.currentTimeMillis() + token.getExpiresIn() * 1000L);
						Files.writeString(Path.of("data/user", uuid.toString()), json.toString(), Charset.forName("utf-8"));
						Response res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.TEMPORARY_REDIRECT, "text/html", "Success");
						res.addHeader("Location", destination);
						return res;
					}
					catch(Exception e) {
						return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/html", "Failed to generate token");
					}
				}
				else {
					String err = "Bad state, please try signing in again";
					if(params.containsKey("error")) {
						err += " (" + params.get("error") + ": " + params.get("error_description") + ")";
					}
					return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.PRECONDITION_FAILED, "text/html", err);
				}
			}
			catch(IllegalArgumentException e) {
				return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.EXPECTATION_FAILED, "text/html", "Invalid data received. Clear cookies and try again.");
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/html", e.toString());
		}
	}
	
	protected static String getCookieFromLogoutToken(UUID token) {
		try {
			File file = new File("data/user/logout/" + token);
			if(file.exists()) {
				return Files.readString(file.toPath(), Charset.forName("utf-8"));
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	protected static JSONObject readUserData(UUID uuid) throws Exception
	{
		File file = new File("data/user/" + uuid);
		if(!file.exists() || file.isDirectory()) {
			return null;
		}
		return new JSONObject(Files.readString(file.toPath(), Charset.forName("utf-8")));
	}
	
	protected static void writeUserData(UUID uuid, JSONObject json) throws Exception {
		File file = new File("data/user/" + uuid);
		String logout;
		if(!json.has("logout")) {
			json.put("logout", UUID.randomUUID().toString());
		}
		logout = json.getString("logout");
		File logoutFile = new File("data/user/logout/" + logout);
		Files.writeString(logoutFile.toPath(), uuid.toString(), Charset.forName("utf-8"));
		Files.writeString(file.toPath(), json.toString(), Charset.forName("utf-8"));
	}
	protected static String getToken(UUID uuid) {
		try {
			JSONObject json = readUserData(uuid);
			if(json == null) {
				return null;
			}
			if(json.getLong("expiry") <= System.currentTimeMillis()) {
				OAuth2AccessToken token = Main.OAUTH_SERVICE.refreshAccessToken(json.getString("refresh"));
				json.put("expiry", System.currentTimeMillis() + token.getExpiresIn() * 1000L);
				json.put("refresh", token.getRefreshToken());
				json.put("token", token.getAccessToken());
				writeUserData(uuid, json);
			}
			return json.getString("token");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	protected static String getLogoutToken(UUID uuid) {
		try {
			JSONObject json = readUserData(uuid);
			if(!json.has("logout")) {
				writeUserData(uuid, json);
			}
			return json.getString("logout");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	protected static void deleteUserData(UUID uuid) {
		try {
			JSONObject json = readUserData(uuid);
			if(json.has("logout")) {
				Files.delete(Path.of("data", "user", "logout", json.getString("logout")));
			}
			Files.delete(Path.of("data", "user", uuid.toString()));
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
