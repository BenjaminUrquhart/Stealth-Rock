package net.benjaminurquhart.stealthrock;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class Attachment {
	
	private static final OkHttpClient CLIENT = new OkHttpClient();
	private static final Route.CompiledRoute REGENERATE_ATTACHMENT_URLS = Route.post("attachments/refresh-urls").compile();

	public static enum Type {
		UNKNOWN,
		IMAGE,
		VIDEO,
		OTHER
	}
	
	public static Attachment read(long lower, long upper) throws IOException {
		return read(new UUID(upper, lower));
	}
	
	public static Attachment read(UUID uuid) throws IOException {
		File file = new File("data/" + uuid);
		if(!file.exists()) {
			return null;
		}
		RandomAccessFile fs = ModmailUtil.getStream(file, 5L);
		fs.seek(0);
		
		Type type = Type.values()[fs.read()];
		String mediaType = null;
		byte[] bytes;
		if(type == Type.IMAGE || type == Type.VIDEO) {
			bytes = new byte[fs.readInt()];
			fs.read(bytes);
			mediaType = new String(bytes, "utf-8");
		}
		bytes = new byte[fs.readInt()];
		fs.read(bytes);
		
		Attachment a = new Attachment(uuid, type, new String(bytes, "utf-8"));
		a.mediaType = mediaType;
		return a;
	}
	
	public static void refreshBulk(JDA jda, Collection<Attachment> attachments) {
		DataObject data = DataObject.empty();
		Object[] expired = attachments.stream().filter(Attachment::expired).map(a -> a.url).toArray();
		if(expired.length == 0) {
			return;
		}
		data.put("attachment_urls", expired);
		Map<String, Attachment> mapping = new HashMap<>();
		RestAction<DataObject> action = new RestActionImpl<DataObject>(jda, REGENERATE_ATTACHMENT_URLS, data, (res, req) -> {
			return res.optObject().orElse(null);
		});
		for(Attachment a : attachments) {
			mapping.put(a.url, a);
		}
		DataObject ret = action.complete(), entry;
		DataArray arr = ret.getArray("refreshed_urls");
		int len = arr.length();
		Attachment a;
		for(int i = 0; i < len; i++) {
			entry = arr.getObject(i);
			a = mapping.get(entry.getString("original"));
			a.url = entry.getString("refreshed");
			try {
				a.save();
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public Type type;
	public String url;
	public String mediaType;
	
	public UUID uuid;
	
	public Attachment(Type type, String url) {
		this(UUID.randomUUID(), type, url);
	}
	
	public Attachment(UUID uuid, Type type, String url) {
		this.uuid = uuid;
		this.type = type;
		this.url = url;
	}
	
	public void save() throws IOException {
		byte[] bytes;
		RandomAccessFile fs = ModmailUtil.getStream(new File("data/" + uuid));
		fs.seek(0);
		
		fs.write(type.ordinal());
		if(type == Type.VIDEO || type == Type.IMAGE) {
			bytes = mediaType.getBytes("utf-8");
			fs.writeInt(bytes.length);
			fs.write(bytes);
		}
		bytes = url.getBytes("utf-8");
		fs.writeInt(bytes.length);
		fs.write(bytes);
	}
	
	public Type getType() {
		if(type == null) {
			type = Type.UNKNOWN;
		}
		if(type == Type.UNKNOWN) {
			Request req = new Request.Builder().head().url(url).build();
			try {
				Response res = CLIENT.newCall(req).execute();
				int code = res.code();
				if(code == 200) {
					ResponseBody body = res.body();
					MediaType resType = body.contentType();
					System.out.println(resType);
					if(resType.type().equals("image")) {
						type = Type.IMAGE;
					}
					else if(resType.type().equals("video")) {
						type = Type.VIDEO;
					}
					else {
						System.out.printf("Unknown content type: %s/%s\n", resType.type(), resType.subtype());
						type = Type.OTHER;
						return type;
					}
					mediaType = String.format("%s/%s", resType.type(), resType.subtype());
					try {
						save();
					}
					catch(Exception e) {
						e.printStackTrace();
					}
				}
				else {
					System.err.printf("Error %s when checking content type\n", code);
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		return type;
	}
	

	public boolean expired() {
		long expireTime;
		HttpUrl url = HttpUrl.parse(this.url);
		String param = url.queryParameter("ex");
		if(param != null) {
			try {
				expireTime = Long.parseLong(param, 16);
			}
			catch(NumberFormatException e) {
				return true;
			}
		}
		else {
			expireTime = -1;
		}
		return expireTime <= (System.currentTimeMillis() / 1000L);
	}
	
	@Override
	public boolean equals(Object other) {
		if(other == null) {
			return false;
		}
		if(this == other) {
			return true;
		}
		if(other instanceof Attachment a) {
			return a.url == url && a.type == type;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return url.hashCode();
	}
	
	@Override
	public String toString() {
		return type.name() + " " + url;
	}
}
