package net.benjaminurquhart.stealthrock;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class Cache<K, V> {
	
	public static final int DEFAULT_CACHE_EXPIRE_SECONDS = 300;
	
	private boolean refresh;
	private long expireTimeSeconds;

	private Map<K, V> cache = Collections.synchronizedMap(new WeakHashMap<>());
	private Map<K, Long> expireTimes = Collections.synchronizedMap(new WeakHashMap<>());
	
	public Cache() {
		this(DEFAULT_CACHE_EXPIRE_SECONDS);
	}
	public Cache(boolean refresh) {
		this(DEFAULT_CACHE_EXPIRE_SECONDS, refresh);
	}
	public Cache(long expireTimeSeconds) {
		this(expireTimeSeconds, false);
	}
	public Cache(long expireTimeSeconds, boolean refresh) {
		this.expireTimeSeconds = expireTimeSeconds;
		this.refresh = refresh;
	}
	
	public V get(K key) {
		if(cache.containsKey(key)) {
			if(expireTimes.get(key) >= System.currentTimeMillis()) {
				if(refresh) {
					expireTimes.put(key, System.currentTimeMillis()+expireTimeSeconds*1000L);
				}
				return cache.get(key);
			}
		}
		expireTimes.remove(key);
		cache.remove(key);
		return null;
	}
	public void set(K key, V value) {
		set(key, value, expireTimeSeconds);
	}
	public void set(K key, V value, long expireTimeSeconds) {
		if(expireTimeSeconds < 0) {
			expireTimes.put(key, Long.MAX_VALUE);
		}
		else {
			expireTimes.put(key, System.currentTimeMillis()+expireTimeSeconds*1000L);
		}
		cache.put(key, value);
	}
	
	public V remove(K key) {
		expireTimes.remove(key);
		return cache.remove(key);
	}
}

