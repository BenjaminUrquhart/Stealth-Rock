package net.benjaminurquhart.stealthrock;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

import org.json.JSONObject;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.CookieHandler;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.router.RouterNanoHTTPD.GeneralHandler;
import fi.iki.elonen.router.RouterNanoHTTPD.UriResource;

public class LogoutHandler extends GeneralHandler {
	
	@SuppressWarnings("deprecation")
	public Response get(UriResource uri, Map<String, String> params, IHTTPSession session) {
		try {
			CookieHandler cookies = session.getCookies();
			String cookie = cookies.read("id");
			if(cookie == null) {
				String logoutToken = session.getParms().get("id");
				if(logoutToken != null) {
					cookie = AuthHandler.getCookieFromLogoutToken(UUID.fromString(logoutToken));
				}
			}
			if(cookie == null) {
				return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/html", "Not signed in");
			}
			UUID id = UUID.fromString(cookie);
			File file = new File("data/user/" + id);
			if(!file.exists()) {
				return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/html", "Already signed out");
			}
			try {
				JSONObject json = new JSONObject(Files.readString(file.toPath(), Charset.forName("utf-8")));
				if(json.getLong("expiry") > System.currentTimeMillis() - 1000) {
					// if we're within 1 second the token is gonna expire anyway
					// so it's not worth the trouble.
					Main.OAUTH_SERVICE.revokeToken(json.getString("token"));
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			finally {
				AuthHandler.deleteUserData(id);
			}
			return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html", "Successfully signed out");
		}
		catch(Exception e) {
			e.printStackTrace();
			return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/html", e.toString());
		}
	}
}
