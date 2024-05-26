package net.benjaminurquhart.stealthrock.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class Config {

	public static TextChannel getLogChannel(Guild guild) {
		File file = new File("data/" + guild.getId() + "/logchannel");
		if(file.exists()) {
			try {
				return guild.getTextChannelById(Files.readString(file.toPath()));
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		List<TextChannel> channels = guild.getTextChannelsByName("modmail-log", true);
		PermissionOverride override;
		for(TextChannel channel : channels) {
			override = channel.getPermissionOverride(guild.getPublicRole());
			if(override != null && override.getDenied().contains(Permission.VIEW_CHANNEL)) {
				return channel;
			}
		}
		return channels.isEmpty() ? null : channels.get(0);
	}
	
	public static boolean setLogChannel(TextChannel channel) throws IOException {
		File folder = new File("data/" + channel.getGuild().getId());
		if(!folder.mkdirs()) {
			return false;
		}
		Files.write(new File(folder, "logchannel").toPath(), channel.getId().getBytes());
		return true;
	}
}
