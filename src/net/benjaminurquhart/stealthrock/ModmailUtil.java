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
import java.util.regex.Pattern;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.TimeUtil;

public class ModmailUtil {
	
	public static enum EntryType {
		NEW_MESSAGE,
		EDIT_MESSAGE,
		DELETE_MESSAGE,
		NEW_MESSAGE_WITH_ATTACHMENTS
	}
	
	private static final Pattern MODMAIL_TOPIC_REGEX = Pattern.compile("ModMail Channel (\\d+) \\d+ \\(Please do not change this\\)");
	
	private static final Map<String, RandomAccessFile> FILESTREAMS = Collections.synchronizedMap(new HashMap<>());
	private static final Map<String, Long> EXPIRY = Collections.synchronizedMap(new HashMap<>());
	
	private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1);
	/*
	public static final Consumer<Message> TEXT_EDIT_HOOK = m -> {
		String url = m.getAttachments().get(0).getUrl();
		
		m.editMessage(String.format(
				"[View in browser](https://txt.discord.website/?txt=%s)",
				url.substring(url.indexOf("s/") + 2, url.length() - 4)
		)).queue();
	};*/
	
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
	
	public static RandomAccessFile getStream(File file) {
		return FILESTREAMS.computeIfAbsent(file.getAbsolutePath(), $ -> {
			try {
				RandomAccessFile fs = new RandomAccessFile(file, "rwd");
				EXPIRY.put($, (System.currentTimeMillis() / 1000) + 15L);
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
		return getThreadOwnerID(channel.getGuild().getIdLong(), channel.getIdLong());
	}
	
	public static long getThreadOwnerID(long guild, long channel) throws IOException {
		if(!hasLogFile(guild, channel)) {
			return -1;
		}
		try {
			RandomAccessFile fs = getLogStream(guild, channel);
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
			
			boolean ownerMsg = false, hasDesc = false;
			
			Set<Attachment> attachments = new HashSet<>();
			for(MessageEmbed embed : msg.getEmbeds()) {
				// Modmail 
				ownerMsg |= author == 575252669443211264L && "Message Received".equals(embed.getTitle());
				hasDesc = embed.getDescription() != null && !embed.getDescription().isEmpty();
				
				if(ownerMsg) {
					author = getThreadOwnerID(msg.getChannel().asTextChannel());
					if(hasDesc) {
						text += embed.getDescription();
					}
					for(Field field : embed.getFields()) {
						if(field.getName() != null && field.getName().startsWith("Attachment")) {
							attachments.add(new Attachment(Attachment.Type.UNKNOWN, field.getValue()));
						}
					}
				}
				else if(embed.getType() == EmbedType.IMAGE) {
					attachments.add(new Attachment(Attachment.Type.IMAGE, embed.getImage().getUrl()));
				}
				else if(embed.getType() == EmbedType.VIDEO) {
					attachments.add(new Attachment(Attachment.Type.VIDEO, embed.getVideoInfo().getUrl()));
				}
			}
			if(!msg.getAttachments().isEmpty() && !ownerMsg) {
				if(text.isEmpty()) {
					text += " ";
				}
				text += "[" + msg.getAttachments().size() + " attachments not logged]";
			}
			
			// Skip empty messages
			if(text.isEmpty() && attachments.isEmpty()) {
				return true;
			}/*
			else if(!text.isEmpty()) {
				text += " ";
			}

			text += String.join(" ", attachments);*/
			
			byte[] bytes = text.getBytes("utf-8");
			fs.seek(fs.length());
			if(attachments.isEmpty()) {
				fs.write(EntryType.NEW_MESSAGE.ordinal());
			}
			else {
				fs.write(EntryType.NEW_MESSAGE_WITH_ATTACHMENTS.ordinal());
			}
			fs.writeLong(author);
			fs.writeLong(msg.getIdLong());
			fs.writeInt(bytes.length);
			fs.write(bytes);
			if(!attachments.isEmpty()) {
				fs.write(attachments.size());
				RandomAccessFile attachFile;
				for(Attachment a : attachments) {
					attachFile = getStream(new File("data/" + a.uuid));
					bytes = a.url.getBytes("utf-8");
					attachFile.write(a.type.ordinal());
					attachFile.writeInt(bytes.length);
					attachFile.write(bytes);
					fs.writeLong(a.uuid.getLeastSignificantBits());
					fs.writeLong(a.uuid.getMostSignificantBits());
				}
			}
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
			byte[] bytes = text.getBytes("utf-8");
			
			RandomAccessFile fs = getLogStream(msg.getChannel().asTextChannel());
			fs.seek(fs.length());
			fs.write(EntryType.EDIT_MESSAGE.ordinal());
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
			fs.write(EntryType.DELETE_MESSAGE.ordinal());
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
			RandomAccessFile fs = getLogStream(guildID, channelID);
			fs.seek(0);
			long authorID = fs.readLong();
			
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
			
			List<LoggedMessage> messages = readLoggedMessages(jda, fs);
			
			Map<Long, User> users = new HashMap<>();
			if(author != null) {
				users.put(author.getIdLong(), author);
			}
			for(LoggedMessage msg : messages) {
				if(msg.authorID != -1) {
					author = users.computeIfAbsent(msg.authorID, $ -> jda.retrieveUserById($).complete());
				}
				sb.append("\r\n\r\n[");
				sb.append(TimeUtil.getTimeCreated(msg.messageID).format(DateTimeFormatter.RFC_1123_DATE_TIME));
				sb.append("] ");
				if(msg.authorID == -1) {
					sb.append("????? : ");
				}
				else {
					sb.append(author.getName());
					sb.append("#0000 : ");
				}
				if(msg.deleted) {
					sb.append("``` ");
				}
				sb.append(msg.text);
				for(Attachment attachment : msg.attachments) {
					sb.append(" ");
					sb.append(attachment.url);
				}
				if(msg.deleted) {
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
	
	public static List<LoggedMessage> readLoggedMessages(JDA jda,long guildID, long channelID) {
		return readLoggedMessages(jda, getLogStream(guildID, channelID));
	}
	
	public static List<LoggedMessage> readLoggedMessages(JDA jda, RandomAccessFile fs) {
		try {
			List<LoggedMessage> out = new ArrayList<>();
			Map<Long, LoggedMessage> messages = new HashMap<>();
			fs.seek(16);
			
			String text;
			byte[] bytes;
			EntryType type;
			long authorID, msgID;
			LoggedMessage msg;
			while(fs.getFilePointer() < fs.length()) {
				type = EntryType.values()[fs.read()];
				switch(type) {
				case NEW_MESSAGE:
				case NEW_MESSAGE_WITH_ATTACHMENTS: {
					authorID = fs.readLong();
					msgID = fs.readLong();
					bytes = new byte[fs.readInt()];
					fs.read(bytes);
					text = new String(bytes, "utf-8");
					
					msg = new LoggedMessage(authorID, msgID, text);
					if(type == EntryType.NEW_MESSAGE_WITH_ATTACHMENTS) {
						int numAttachments = fs.read();
						for(int i = 0; i < numAttachments; i++) {
							msg.attachments.add(Attachment.read(fs.readLong(), fs.readLong()));
						}
					}
					out.add(msg);
					messages.put(msgID, msg);
				} break;
				case EDIT_MESSAGE: {
					msgID = fs.readLong();
					bytes = new byte[fs.readInt()];
					fs.read(bytes);
					text = new String(bytes, "utf-8");
					msg = messages.get(msgID);
					if(msg == null) {
						msg = new LoggedMessage(-1, msgID, text);
						messages.put(msgID, msg);
						out.add(msg);
					}
					else {
						msg.text = text;
					}
				} break;
				case DELETE_MESSAGE: {
					msgID = fs.readLong();
					msg = messages.get(msgID);
					if(msg != null) {
						msg.deleted = true;
					}
				} break;
				}
			}
			
			return out;
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static void sendLogs(long id, TextChannel logChannel) {
		String logText = retrieveLogs(logChannel.getGuild().getIdLong(), id, logChannel.getJDA());
		if(logText == null) {
			logChannel.sendMessage("An error occured while parsing log file. The raw dump is attached instead.")
					  .addFiles(FileUpload.fromData(getLogFile(logChannel.getGuild().getIdLong(), id), "raw.bin"))
					  .queue();
		}
		else {
			logChannel.sendFiles(FileUpload.fromData(logText.getBytes(), "logs.txt"))
					  .addContent(getMarkdownUrl(logChannel.getGuild().getIdLong(), id))
					  .queue();
		}
	}
	
	public static String getMarkdownUrl(long guildID, long channelID) {
		return String.format("[View in browser](%s%s/%s)\n", Main.BASE_URL, Long.toUnsignedString(guildID), Long.toUnsignedString(channelID));
	}
}
