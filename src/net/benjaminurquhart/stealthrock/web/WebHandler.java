package net.benjaminurquhart.stealthrock.web;

import java.io.File;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.CookieHandler;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.router.RouterNanoHTTPD.GeneralHandler;
import fi.iki.elonen.router.RouterNanoHTTPD.UriResource;
import net.benjaminurquhart.stealthrock.LoggedMessage;
import net.benjaminurquhart.stealthrock.Main;
import net.benjaminurquhart.stealthrock.ModmailUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.RestConfig;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.utils.TimeUtil;

import static j2html.TagCreator.*;

public class WebHandler extends GeneralHandler {
	
	public static final Pattern URL_REGEX = Pattern.compile("^/?(\\d+)/(\\d+)$");

	@Override
	public Response get(UriResource uriResource, Map<String, String> urlParams, IHTTPSession session) {
		long start = System.currentTimeMillis();
		String url = session.getUri();
		//System.out.println(url);
		Matcher matcher = URL_REGEX.matcher(url);
		CookieHandler cookies = session.getCookies();
		String logoutLink = "/logout";
		Response res = NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "Not found");
		if(matcher.find()) {
			try {
				JDA jda = Main.jda;
				long guild = Long.parseLong(matcher.group(1));
				long channel = Long.parseLong(matcher.group(2));
				
				Guild server = jda.getGuildById(guild);
				if(server == null) {
					// cookies don't matter here
					return res;
				}
				
				Member member = null;
				String username = "???";
				
				boolean authorized = false;
				String token = null;
				UUID uuid = null;
				try {
					if(cookies.read("id") != null) {
						uuid = UUID.fromString(cookies.read("id"));
						token = AuthHandler.getToken(uuid);
					}
					else {
						uuid = UUID.randomUUID();
					}
				}
				catch(Exception e) {
					uuid = UUID.randomUUID();
				}
				
				if(token != null) {
					try {
						OAuthRequest request = new OAuthRequest(Verb.GET, RestConfig.DEFAULT_BASE_URL + Route.Self.GET_SELF.getRoute());
						Main.OAUTH_SERVICE.signRequest(token, request);
						try(com.github.scribejava.core.model.Response response = Main.OAUTH_SERVICE.execute(request)) {
							int code = response.getCode();
							JSONObject body = new JSONObject(response.getBody());
							//System.out.println(body);
							if(response.getCode() == 200) {
								member = server.retrieveMemberById(body.getString("id")).complete();
								if(member != null) {
									authorized = member.hasPermission(Permission.MESSAGE_MANAGE);
									User user = member.getUser();
									if(user.getGlobalName() == null) {
										username = user.getName();
									}
									else {
										username = user.getGlobalName() + " (" + user.getName() + ")";
									}
									logoutLink += "?id=" + AuthHandler.getLogoutToken(uuid);
								}
								else if(body.optString("global_name", null) != null){
									username = body.getString("global_name") + " (" + body.getString("username") + ")";
								}
								else {
									username = body.getString("username");
								}
								System.out.printf("[%s] %s (%s) accessed %s (server: %s, authorized: %s)\n", OffsetDateTime.now(), username, body.getString("id"), matcher.group(0), server.getName(), authorized);
							}
							else {
								AuthHandler.deleteUserData(uuid);
								System.err.println("Error getting user info: " + code);
								System.err.println(body);
								token = null;
							}
						}
					}
					catch(Exception e) {
						e.printStackTrace();
						//Files.deleteIfExists(Path.of("data", uuid.toString()));
						token = null;
					}
				}
				if(token == null) {
					// 5 minute timeout
					Main.PENDING_AUTH.put(uuid.toString(), System.currentTimeMillis() + 1000 * 60 * 5);
					res = NanoHTTPD.newFixedLengthResponse(
							NanoHTTPD.Response.Status.TEMPORARY_REDIRECT, 
							"text/html", 
							"Redirecting for login..."
					);
					res.addHeader("Location", Main.OAUTH_SERVICE.getAuthorizationUrl(uuid.toString() + "=" + matcher.group(1) + "=" + matcher.group(2)));
					cookies.set("id", uuid.toString() + "; Secure; HttpOnly", 7);
				}
				else if(!authorized) {
					res = NanoHTTPD.newFixedLengthResponse(
							NanoHTTPD.Response.Status.FORBIDDEN, 
							"text/html", 
							"Unauthorized<br><a herf='" + logoutLink + "'>Log out</a>"
					);
					cookies.set("id", uuid.toString() + "; Secure; HttpOnly", 7);
				}
				else {
					File file = new File("data/" + matcher.group(0));
					if(file.exists()) {
						long ownerID = ModmailUtil.getThreadOwnerID(guild, channel);
						List<LoggedMessage> messages = ModmailUtil.readLoggedMessages(jda, guild, channel);
						User owner = ownerID == -1 ? null : jda.retrieveUserById(ownerID).complete();
						String name = owner == null ? "<unknown user>" : owner.getName();
						Map<Long, User> users = new HashMap<>();
						res = NanoHTTPD.newFixedLengthResponse(html(
								head(
										title("Stealth Rock"),
										link().withRel("stylesheet").withHref("https://cdn.jsdelivr.net/gh/kognise/water.css@latest/dist/dark.min.css")
								),
								body(
										p(a(username + " - Log out").withHref(logoutLink)).withStyle("font-size: 75%; color: #ffffff"),
										h2(String.format("Ticket opened by %s (%s)", name, Long.toUnsignedString(ownerID))),
										br(),
										each(messages, msg -> {
											User user = users.computeIfAbsent(msg.authorID, $ -> jda.retrieveUserById($).complete());
											return div(
													p(String.format("[%s] %s", TimeUtil.getTimeCreated(msg.messageID).format(DateTimeFormatter.RFC_1123_DATE_TIME), user.getName())).withStyle("font-size: 75%"),
													p(msg.text),
													br(),
													each(msg.attachments, a -> {
														if(a == null) {
															return p("[missing attachment]").withStyle("color: #7f7f7f");
														}
														switch(a.getType()) {
														case IMAGE: return img().withSrc(a.url);
														case VIDEO: return video(source().withSrc(a.url).withType(a.mediaType));
														default: return a().withHref(a.url).with(p(a.url));
														}
													}),
													br()
											).withStyle("color: #00ffff")
											 .withCondStyle(msg.authorID == ownerID, "color: #ffff00")
											 .withCondStyle(msg.deleted,             "color: #7f7f7f");
										}),
										br(),
										p("Took " + (System.currentTimeMillis() - start) + "ms").withStyle("color: #9f9f9f")
								)
						).render());
					}
				}
			}
			catch(Exception e) {
				e.printStackTrace();
				res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/html", e.toString());
			}
		}
		cookies.unloadQueue(res);
		return res;
		
	}
}
