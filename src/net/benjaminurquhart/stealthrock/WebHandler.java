package net.benjaminurquhart.stealthrock;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.router.RouterNanoHTTPD.GeneralHandler;
import fi.iki.elonen.router.RouterNanoHTTPD.UriResource;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.TimeUtil;

import static j2html.TagCreator.*;

public class WebHandler extends GeneralHandler {
	
	public static final Pattern URL_REGEX = Pattern.compile("^/?(\\d+)/(\\d+)$");

	@Override
	public Response get(UriResource uriResource, Map<String, String> urlParams, IHTTPSession session) {
		String url = session.getUri();
		System.out.println(url);
		Matcher matcher = URL_REGEX.matcher(url);
		if(matcher.find()) {
			try {
				File file = new File("data/" + matcher.group(0));
				if(file.exists()) {
					JDA jda = Main.jda;
					long guild = Long.parseLong(matcher.group(1));
					long channel = Long.parseLong(matcher.group(2));
					long ownerID = ModmailUtil.getThreadOwnerID(guild, channel);
					List<LoggedMessage> messages = ModmailUtil.readLoggedMessages(jda, guild, channel);
					User owner = ownerID == -1 ? null : jda.retrieveUserById(ownerID).complete();
					String name = owner == null ? "<unknown user>" : owner.getName();
					Map<Long, User> users = new HashMap<>();
					return NanoHTTPD.newFixedLengthResponse(html(
							head(
									title("Stealth Rock"),
									link().withRel("stylesheet").withHref("https://cdn.jsdelivr.net/gh/kognise/water.css@latest/dist/dark.min.css")
							),
							body(
									h2(String.format("Ticket opened by %s (%s)", name, Long.toUnsignedString(ownerID))),
									br(),
									each(messages, msg -> {
										User user = users.computeIfAbsent(msg.authorID, $ -> jda.retrieveUserById($).complete());
										return div(
												p(String.format("[%s] %s", TimeUtil.getTimeCreated(msg.messageID).format(DateTimeFormatter.RFC_1123_DATE_TIME), user.getName())).withStyle("font-size: 75%"),
												p(msg.text),
												br(),
												each(msg.attachments, a -> {
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
									})
							)
					).render());
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "Not found");
	}
}
