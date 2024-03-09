package net.benjaminurquhart.stealthrock;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutionException;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class GuildUtil {
	
	public static class InviteData {
		
	}
	
	public static final Map<String, Set<InviteData>> INVITE_DATA = new HashMap<>(); 
	
	public static void refreshGuildData(Guild guild) {
		for(TextChannel channel : guild.getTextChannels()) {
			if(ModmailUtil.isModmailChannel(channel)) {
				try {
					if(!ModmailUtil.hasLogFile(channel)) {
						ModmailUtil.createLogFile(channel);
					}
					backupMissingMessages(channel);
				}
				catch(Exception e) {
					channel.sendMessage("Failed to resume logging this channel after restart: " + e).queue();
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void backupMissingMessages(TextChannel channel) throws IOException, InterruptedException, ExecutionException {
		long last = ModmailUtil.getLastRecordedMessageID(channel);
		Stack<Message> stack = new Stack<>();
		if(last == -1) {
			last = 0;
		}
		final long limit = last;
		channel.getIterableHistory().takeWhileAsync(m -> m.getIdLong() != limit).whenCompleteAsync((history, e) -> {
			long self = channel.getJDA().getSelfUser().getIdLong();
			for(Message msg : history) {
				if(msg.getIdLong() == limit || msg.getAuthor().getIdLong() == self) continue;
				stack.push(msg);
			}
			while(!stack.isEmpty()) {
				ModmailUtil.logMessage(stack.pop());
			}
		});
	}
}
