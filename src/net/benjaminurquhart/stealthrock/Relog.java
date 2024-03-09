package net.benjaminurquhart.stealthrock;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

public class Relog extends ListenerAdapter {
	
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if(!event.getName().equals("relog")) {
			return;
		}
		ReplyCallbackAction reply = event.deferReply(false);
		
		if(!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
			reply.addContent("You do not have permission to use this command.").setEphemeral(true).queue();
			return;
		}
		
		if(!ModmailUtil.isModmailChannel(event.getChannel())) {
			reply.addContent("This command must be used in a modmail thread.").setEphemeral(true).queue();
			return;
		}
		
		reply.addContent("I didn't finish this lol").queue();
	}
}
