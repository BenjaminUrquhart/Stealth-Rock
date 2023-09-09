package net.benjaminurquhart.stealthrock;

import java.io.IOException;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ModmailListener extends ListenerAdapter {
	
	private static final Pattern DUMP_REGEX = Pattern.compile("<@!?(\\d+)>\\s+dump\\s+(\\d+)");
	
	private void resumeLoggingChannels(Guild guild) {
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
	private void backupMissingMessages(TextChannel channel) throws IOException, InterruptedException, ExecutionException {
		long last = ModmailUtil.getLastRecordedMessageID(channel);
		Stack<Message> stack = new Stack<>();
		if(last == -1) {
			last = 0;
		}
		final long limit = last;
		List<Message> history = channel.getIterableHistory().takeWhileAsync(m -> m.getIdLong() != limit).get();
		long self = channel.getJDA().getSelfUser().getIdLong();
		for(Message msg : history) {
			if(msg.getIdLong() == limit || msg.getAuthor().getIdLong() == self) continue;
			stack.push(msg);
		}
		while(!stack.isEmpty()) {
			ModmailUtil.logMessage(stack.pop());
		}
	}

	@Override
	public void onChannelCreate(ChannelCreateEvent event) {
		if(!event.isFromType(ChannelType.TEXT)) {
			return;
		}
		TextChannel channel = event.getChannel().asTextChannel();
		if(ModmailUtil.isModmailChannel(channel)) {
			try {
				if(ModmailUtil.createLogFile(channel)) {
					channel.sendMessage("Message logging started. Attachments will **not** be saved.").queue();
				}
				else {
					channel.sendMessage("Failed to start logging. **Please manually archive this channel before closing this ticket.**").queue();
				}
			}
			catch(IOException e) {
				e.printStackTrace();
				channel.sendMessage("An internal error occured while starting the logging process:\n```" + e + "```\n\n**Please manually archive this channel before closing this ticket.**").queue();
			}
		}
	}
	
	@Override
	public void onChannelDelete(ChannelDeleteEvent event) {
		if(!event.isFromGuild() || !ModmailUtil.isModmailChannel(event.getChannel())) {
			return;
		}
		TextChannel logChannel = ModmailUtil.getLogChannel(event.getGuild());
		TextChannel channel = event.getChannel().asTextChannel();
		
		ModmailUtil.sendLogs(channel.getIdLong(), logChannel);
	}
	
	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		User author = event.getAuthor();
		Message msg = event.getMessage();
		if(author.getId().equals("273216249021071360")) {
			Matcher matcher = DUMP_REGEX.matcher(msg.getContentRaw());
			if(matcher.matches() && msg.getMentions().isMentioned(event.getJDA().getSelfUser(), MentionType.USER)) {
				ModmailUtil.sendLogs(Long.parseUnsignedLong(matcher.group(2)), event.getChannel().asTextChannel());
				return;
			}
		}
		if(!event.isFromGuild() || !ModmailUtil.isModmailChannel(event.getChannel())) {
			return;
		}
		if(author.getId().equals(event.getJDA().getSelfUser().getId())) {
			return;
		}
		
		ModmailUtil.logMessage(msg);
	}
	
	@Override
	public void onMessageUpdate(MessageUpdateEvent event) {
		if(!event.isFromGuild() || !ModmailUtil.isModmailChannel(event.getChannel())) {
			return;
		}
		ModmailUtil.editMessage(event.getMessage());
	}
	
	@Override
	public void onMessageDelete(MessageDeleteEvent event) {
		if(!event.isFromGuild() || !ModmailUtil.isModmailChannel(event.getChannel())) {
			return;
		}
		ModmailUtil.deleteMessage(event.getChannel().asTextChannel(), event.getMessageIdLong());
	}
	
	@Override
	public void onGuildJoin(GuildJoinEvent event) {
		resumeLoggingChannels(event.getGuild());
	}
	
	@Override
	public void onReady(ReadyEvent event) {
		for(Guild guild : event.getJDA().getGuilds()) {
			resumeLoggingChannels(guild);
		}
	}
}