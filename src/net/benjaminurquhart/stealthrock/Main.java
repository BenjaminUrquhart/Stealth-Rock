package net.benjaminurquhart.stealthrock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumSet;

import org.json.JSONObject;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class Main {
	
	public static final String ZWS = new StringBuilder().appendCodePoint(0x17b5).toString();
	
	private static final String TOKEN;
	
	
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
			TOKEN = null;
		}
		else {
			TOKEN = new JSONObject(json).optString("token", null);
		}
	}

	public static void main(String[] args) throws Exception {
		if(TOKEN == null) {
			System.exit(1);
			return;
		}
		
		JDA jda = JDABuilder.createDefault(TOKEN, EnumSet.complementOf(EnumSet.of(GatewayIntent.GUILD_PRESENCES)))
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
						
				Commands.slash("getemup", "Get those rocks up.")
							
		).queue();
	}

}
