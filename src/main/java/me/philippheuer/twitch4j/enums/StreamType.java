package me.philippheuer.twitch4j.enums;

public enum StreamType {
	LIVE,
	PLAYLIST;

	public static StreamType of(String type) {
		if (type.equalsIgnoreCase("live")) {
			return LIVE;
		} else {
			return PLAYLIST;
		}
	}
}
