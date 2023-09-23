package net.benjaminurquhart.stealthrock;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.TimeUtil;

public class ModmailUtil {
	
	private static final byte NEW_MESSAGE    = 0;
	private static final byte EDIT_MESSAGE   = 1;
	private static final byte DELETE_MESSAGE = 2;
	
	private static final Pattern MODMAIL_TOPIC_REGEX = Pattern.compile("ModMail Channel (\\d+) \\d+ \\(Please do not change this\\)");
	
	private static final Map<String, RandomAccessFile> FILESTREAMS = Collections.synchronizedMap(new HashMap<>());
	private static final Map<String, Long> EXPIRY = Collections.synchronizedMap(new HashMap<>());
	
	private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1);
	
	public static final Consumer<Message> TEXT_EDIT_HOOK = m -> {
		String url = m.getAttachments().get(0).getUrl();
		
		m.editMessage(String.format(
				"[View in browser](https://txt.discord.website/?txt=%s)",
				url.substring(url.indexOf("s/") + 2, url.length() - 4)
		)).queue();
	};
	
	static {
		final Set<String> toRemove = new HashSet<>();
		EXECUTOR.scheduleWithFixedDelay(() -> {
			toRemove.clear();
			for(String channel : EXPIRY.keySet()) {
				if(EXPIRY.get(channel) < System.currentTimeMillis() / 1000) {
					toRemove.add(channel);
				}
			}
			for(String channel : toRemove) {
				try {
					EXPIRY.remove(channel);
					FILESTREAMS.remove(channel).close();
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		}, 60, 60, TimeUnit.SECONDS);
	}
	
	
	private ModmailUtil() {}
	
	public static boolean isModmailChannel(Channel ch) {
		if(ch.getType() != ChannelType.TEXT) {
			return false;
		}
		TextChannel channel = (TextChannel)ch;
		if(hasLogFile(channel)) {
			return true;
		}
		return channel.getTopic() != null && MODMAIL_TOPIC_REGEX.matcher(channel.getTopic()).matches();
	}
	
	public static boolean createLogFile(TextChannel channel) throws IOException {
		File folder = new File("data/" + channel.getGuild().getId());
		if(!folder.exists() && !folder.mkdirs()) {
			return false;
		}
		RandomAccessFile fs = getLogStream(channel);
		fs.seek(0);
		
		Long threadOwner = Long.parseUnsignedLong(channel.getTopic().split("\\s+")[2]);
		fs.writeLong(threadOwner);
		
		return true;
	}
	
	public static File getLogFile(TextChannel channel) {
		return getLogFile(channel.getGuild().getIdLong(), channel.getIdLong());
	}
	
	public static File getLogFile(long guildID, long channelID) {
		return new File("data/" + Long.toUnsignedString(guildID) + "/" + Long.toUnsignedString(channelID));
	}
	
	public static boolean hasLogFile(TextChannel channel) {
		return hasLogFile(channel.getGuild().getIdLong(), channel.getIdLong());
	}
	
	public static boolean hasLogFile(long guildID, long channelID) {
		return Files.exists(Path.of("data", Long.toUnsignedString(guildID), Long.toUnsignedString(channelID)));
	}
	
	private static RandomAccessFile getLogStream(TextChannel channel) {
		return getLogStream(channel.getGuild().getIdLong(), channel.getIdLong());
	}
	
	private static RandomAccessFile getLogStream(long guildID, long channelID) {
		return FILESTREAMS.computeIfAbsent(Long.toUnsignedString(channelID), $ -> {
			try {
				File file = getLogFile(guildID, channelID);
				boolean initialize = !file.exists();
				
				RandomAccessFile fs = new RandomAccessFile(file, "rwd");
				EXPIRY.put($, (System.currentTimeMillis() / 1000) + 300L);
				if(initialize) {
					fs.writeLong(-1);
					fs.writeLong(-1);
				}
				return fs;
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			return null;
		});
	}
	
	public static long getLastRecordedMessageID(TextChannel channel) throws IOException {
		if(!hasLogFile(channel)) {
			return -1;
		}
		try {
			RandomAccessFile fs = getLogStream(channel);
			fs.seek(8);
			return fs.readLong();
		}
		catch(IOException e) {
			e.printStackTrace();
			return -1;
		}
	}
	
	public static long getThreadOwnerID(TextChannel channel) throws IOException {
		if(!hasLogFile(channel)) {
			return -1;
		}
		try {
			RandomAccessFile fs = getLogStream(channel);
			fs.seek(0);
			return fs.readLong();
		}
		catch(IOException e) {
			e.printStackTrace();
			return -1;
		}
	}
	
	public static boolean logMessage(Message msg) {
		try {
			RandomAccessFile fs = getLogStream(msg.getChannel().asTextChannel());
			
			fs.seek(8);
			fs.writeLong(msg.getIdLong());
			
			String text = msg.getContentDisplay();
			long author = msg.getAuthor().getIdLong();
			
			// Modmail
			if(msg.getAuthor().getId().equals("575252669443211264")) {
				for(MessageEmbed embed : msg.getEmbeds()) {
					if("Message Received".equals(embed.getTitle())) {
						text = embed.getDescription();
						author = getThreadOwnerID(msg.getChannel().asTextChannel());
					}
				}
			}
			// Skip empty messages
			if(text.isEmpty()) {
				return true;
			}
			
			byte[] bytes = text.getBytes();
			fs.seek(fs.length());
			fs.write(NEW_MESSAGE);
			fs.writeLong(author);
			fs.writeLong(msg.getIdLong());
			fs.writeInt(bytes.length);
			fs.write(bytes);
			

			
			return true;
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean editMessage(Message msg) {
		try {
			String text = msg.getContentDisplay();
			byte[] bytes = text.getBytes();
			
			RandomAccessFile fs = getLogStream(msg.getChannel().asTextChannel());
			fs.seek(fs.length());
			fs.write(EDIT_MESSAGE);
			fs.writeLong(msg.getIdLong());
			fs.writeInt(bytes.length);
			fs.write(bytes);
			return true;
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean deleteMessage(TextChannel channel, long id) {
		try {
			RandomAccessFile fs = getLogStream(channel);
			fs.seek(fs.length());
			fs.write(DELETE_MESSAGE);
			fs.writeLong(id);
			return true;
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static String retrieveLogs(TextChannel channel) {
		return retrieveLogs(channel.getGuild().getIdLong(), channel.getIdLong(), channel.getJDA());
	}
	public static String retrieveLogs(long guildID, long channelID, JDA jda) {
		try {
			Map<Long, Long> authors = new HashMap<>();
			Map<Long, String> messages = new HashMap<>();
			Set<Long> deleted = new HashSet<>();
			
			List<Long> messageIDs = new ArrayList<>();
			
			RandomAccessFile fs = getLogStream(guildID, channelID);
			fs.seek(0);
			long authorID = fs.readLong(), msgID;
			
			StringBuilder sb = new StringBuilder();
			User author = null;
			if(authorID != -1) {
				author = jda.retrieveUserById(authorID).complete();
				sb.append("Ticket opened by ");
				sb.append(author.getName());
				sb.append(" (");
				sb.append(author.getId());
				sb.append(")");
			}
			else {
				sb.append("Ticket opened by <unknown user>\n");
			}
			
			fs.seek(16);
			
			int type;
			String text;
			byte[] bytes;
			while(fs.getFilePointer() < fs.length()) {
				type = fs.read();
				if(type == NEW_MESSAGE) {
					authorID = fs.readLong();
					msgID = fs.readLong();
					bytes = new byte[fs.readInt()];
					fs.read(bytes);
					text = new String(bytes);
					
					messages.put(msgID, text);
					authors.put(msgID, authorID);
					messageIDs.add(msgID);
				}
				else if(type == EDIT_MESSAGE) {
					msgID = fs.readLong();
					bytes = new byte[fs.readInt()];
					fs.read(bytes);
					text = new String(bytes);
					messages.put(msgID, text);
				}
				else if(type == DELETE_MESSAGE) {
					msgID = fs.readLong();
					deleted.add(msgID);
				}
			}
			
			boolean isDeleted;
			Map<Long, User> users = new HashMap<>();
			if(author != null) {
				users.put(author.getIdLong(), author);
			}
			for(long id : messageIDs) {
				author = users.computeIfAbsent(authors.get(id), $ -> jda.retrieveUserById($).complete());
				isDeleted = deleted.contains(id);
				sb.append("\r\n\r\n[");
				sb.append(TimeUtil.getTimeCreated(id).format(DateTimeFormatter.RFC_1123_DATE_TIME));
				sb.append("] ");
				sb.append(author.getName());
				sb.append("#0000 : ");
				if(isDeleted) {
					sb.append("``` ");
				}
				sb.append(messages.get(id));
				if(isDeleted) {
					sb.append(" ```");
				}
			}
			return sb.toString().trim();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
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
	
	public static void sendLogs(long id, TextChannel logChannel) {
		String logText = retrieveLogs(logChannel.getGuild().getIdLong(), id, logChannel.getJDA());
		if(logText == null) {
			logChannel.sendMessage("An error occured while parsing log file. The raw dump is attached instead.")
					  .addFiles(FileUpload.fromData(getLogFile(logChannel.getGuild().getIdLong(), id), "raw.bin"))
					  .queue();
		}
		else {
			logChannel.sendFiles(FileUpload.fromData(logText.getBytes(), "logs.txt")).queue(TEXT_EDIT_HOOK);
		}
	}
}
