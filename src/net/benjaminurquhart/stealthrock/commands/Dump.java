package net.benjaminurquhart.stealthrock.commands;

import net.benjaminurquhart.stealthrock.util.ModmailUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.FileUpload;

public class Dump extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if(!event.getName().equals("dump") && !event.getName().equals("dumpid")) {
			return;
		}
		ReplyCallbackAction reply = event.deferReply(false);
		
		if(!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
			reply.addContent("You do not have permission to use this command.").setEphemeral(true).queue();
			return;
		}
		
		long guildID = event.getGuild().getIdLong();
		
		try {
			long id;
			OptionMapping option = event.getOption("channel");
			if(option.getType() == OptionType.CHANNEL)  {
				if(option.getChannelType() != ChannelType.TEXT) {
					reply.addContent("Only text channels are supported.").setEphemeral(true).queue();
					return;
				}
				id = option.getAsChannel().getIdLong();
			}
			else {
				try {
					id = Long.parseUnsignedLong(option.getAsString());
				}
				catch(NumberFormatException e) {
					reply.addContent("Invalid channel ID").setEphemeral(true).queue();
					return;
				}
				
			}
			if(ModmailUtil.hasLogFile(guildID, id)) {
				String text = ModmailUtil.retrieveLogs(guildID, id, event.getJDA());
				if(text == null) {
					reply.addContent("An error occured while parsing log file. The raw dump is attached instead.")
					     .addFiles(FileUpload.fromData(ModmailUtil.getLogFile(guildID, id), "raw.bin"))
					     .queue();
				}
				else {
					reply.addFiles(FileUpload.fromData(text.getBytes(), "log.txt")).addContent(ModmailUtil.getMarkdownUrl(guildID, id)).queue();
				}
			}
			else {
				reply.addContent("No logs found for <#" + Long.toUnsignedString(id) + "> (" + Long.toUnsignedString(id) + ")").queue();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			reply.addContent("Internal error: " + e).queue();
		}
	}
}
