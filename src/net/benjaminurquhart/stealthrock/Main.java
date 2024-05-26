package net.benjaminurquhart.stealthrock;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;

import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.github.scribejava.apis.DiscordApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;

import fi.iki.elonen.router.RouterNanoHTTPD;

import net.benjaminurquhart.stealthrock.commands.*;
import net.benjaminurquhart.stealthrock.web.*;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class Main extends RouterNanoHTTPD {
	
	public static final OAuth20Service OAUTH_SERVICE;
	
	public static final String ZWS = new StringBuilder().appendCodePoint(0x17b5).toString();
	
	// I made this for 1 server there will probably be at most 1 person logging in at a time.
	// I am not the best at thread safety and if there's thousands of people trying to
	// log in then something has gone terribly wrong.
	public static final Map<String, Long> PENDING_AUTH = Collections.synchronizedMap(new HashMap<>());
	
	private static final String TOKEN;
	
	public static final String BASE_URL;
	public static final int PORT;
	
	public static JDA jda;
	
	private static final ScheduledExecutorService CLEANUP = Executors.newSingleThreadScheduledExecutor();
	
	
	// "The final field TOKEN may already have been assigned" WHERE
	static {
		String json = null;
		try {
			json = Files.readString(new File("config.json").toPath());
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		if(json == null) {
			PORT = 8888;
			TOKEN = null;
			BASE_URL = "http://localhost:8888/";
			OAUTH_SERVICE = null;
		}
		else {
			JSONObject config = new JSONObject(json);
			PORT = config.optInt("port", 8888);
			TOKEN = config.optString("token", null);
			BASE_URL = config.optString("base_url", "http://localhost:8888/");
			if(config.has("client_secret") && TOKEN != null) {
				String clientID = new String(Base64.getDecoder().decode(TOKEN.split("\\.")[0]));
				OAUTH_SERVICE = new ServiceBuilder(clientID)
						.apiSecret(config.getString("client_secret"))
						.defaultScope("identify guilds")
						.callback(BASE_URL + "auth")
						.userAgent("Stealth Rock - https://github.com/BenjaminUrquhart/Stealth-Rock")
						.build(DiscordApi.instance());
			}
			else {
				OAUTH_SERVICE = null;
			}
		}
		
		CLEANUP.scheduleWithFixedDelay(() -> {
			synchronized(PENDING_AUTH) {
				for(String id : PENDING_AUTH.keySet()) {
					if(PENDING_AUTH.get(id) <= System.currentTimeMillis()) {
						PENDING_AUTH.remove(id);
					}
				}
			}
		}, 1, 1, TimeUnit.SECONDS);
	}
	
	public Main() {
		super(PORT);
	}

	public static void main(String[] args) throws Exception {
		if(TOKEN == null) {
			System.exit(1);
			return;
		}
		
		File data = new File("data/user/logout");
		if(!data.exists()) {
			data.mkdirs();
		}
		
		//System.setProperty("org.slf4j.Logger.defaultLogLevel", "trace");
		
		jda = JDABuilder.createDefault(TOKEN, EnumSet.complementOf(EnumSet.of(GatewayIntent.GUILD_PRESENCES)))
				.addEventListeners(new ModmailListener(), new Bind(), new Dump(), new GetEmUp())
				.setMemberCachePolicy(MemberCachePolicy.ALL)
				.setBulkDeleteSplittingEnabled(true)
				.setActivity(Activity.watching(ZWS))
				.build();
		
		jda.awaitReady();
		
		jda.updateCommands().addCommands(
				Commands.slash("bind", "Sets the channel to put message logs in.")
						.addOption(OptionType.CHANNEL, "channel", "channel")
						.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)),
				
				Commands.slash("dump", "Dumps the log for the given modmail thread early.")
						.addOption(OptionType.CHANNEL, "channel", "Channel", false)
						.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)),

				Commands.slash("dumpid", "Dumps the log for the given modmail channel ID.")
						.addOption(OptionType.STRING, "channel", "Channel ID", false)
						.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)),
					
				/*
				Commands.slash("relog", "Recreates the internal logfile for this modmail thread.")
						.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)),*/
						
				Commands.slash("getemup", "Get those rocks up.")
		).queue();
		
		Main server = new Main();
		server.addRoute("/auth", AuthHandler.class);
		server.addRoute("/logout", LogoutHandler.class);
		
		// lol
		server.setNotFoundHandler(WebHandler.class);
		server.start(RouterNanoHTTPD.SOCKET_READ_TIMEOUT, false);
	}

}
