package net.benjaminurquhart.stealthrock.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

public class Bind extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if(!event.getName().equals("bind")) {
			return;
		}
		ReplyCallbackAction reply = event.deferReply(true);
		
		if(!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
			reply.addContent("You do not have permission to use this command.").setEphemeral(true).queue();
			return;
		}
		
		GuildChannelUnion option = event.getOption("channel").getAsChannel();
		if(option.getType() != ChannelType.TEXT) {
			reply.addContent("Channel type " + option.getType() + " not allowed").setEphemeral(true).queue();
			return;
		}
		TextChannel channel = option.asTextChannel();
		try {
			if(Config.setLogChannel(channel)) {
				reply.addContent("Log channel set to " + channel.getAsMention()).queue();
			}
			else {
				reply.addContent("Failed to set log channel for an unknown reason").queue();
			}
		}
		catch(Exception e) {
			reply.addContent("Internal error: " + e).queue();
			e.printStackTrace();
		}
	}

}
