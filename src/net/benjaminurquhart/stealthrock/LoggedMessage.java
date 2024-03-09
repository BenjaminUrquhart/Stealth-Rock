package net.benjaminurquhart.stealthrock;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.dv8tion.jda.api.JDA;

public class LoggedMessage {
	
	public long authorID;
	public long messageID;
	public String text;
	public Set<Attachment> attachments;
	public boolean deleted;
	
	private final Object lock = new Object();

	public LoggedMessage(long authorID, long messageID, String text) {
		this(authorID, messageID, text, new HashSet<>());
	}
	
	public LoggedMessage(long authorID, long messageID, String text, Set<Attachment> attachments) {
		this.authorID = authorID;
		this.messageID = messageID;
		this.text = text;
		this.attachments = attachments;
	}
	
	private Set<Attachment> needsRegeneration() {
		if(attachments.isEmpty()) {
			return Collections.emptySet();
		}
		synchronized(lock) {
			Set<Attachment> needsRegen = new HashSet<>();
			for(Attachment attachment : attachments) {
				if(attachment.expired()) {
					needsRegen.add(attachment);
				}
			}
			return needsRegen;
		}
	}
	
	public boolean regenerateAttachments(JDA jda) {
		Set<Attachment> needsRegen = this.needsRegeneration();
		if(needsRegen.isEmpty()) {
			return false;
		}
		synchronized(lock) {
			Attachment.refreshBulk(jda, needsRegen);
		}
		return true;
	}
}
