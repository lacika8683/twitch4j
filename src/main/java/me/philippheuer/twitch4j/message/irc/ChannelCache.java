package me.philippheuer.twitch4j.message.irc;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.github.philippheuer.events4j.EventManager;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.philippheuer.twitch4j.events.event.irc.ChannelModEvent;
import me.philippheuer.twitch4j.events.event.irc.ChannelStateEvent;
import me.philippheuer.twitch4j.events.event.irc.UserBanEvent;
import me.philippheuer.twitch4j.events.event.irc.UserTimeoutEvent;
import me.philippheuer.twitch4j.events.event.irc.roomstates.BroadcasterLanguageEvent;
import me.philippheuer.twitch4j.events.event.irc.roomstates.EmoteOnlyEvent;
import me.philippheuer.twitch4j.events.event.irc.roomstates.FollowersOnlyEvent;
import me.philippheuer.twitch4j.events.event.irc.roomstates.Robot9000Event;
import me.philippheuer.twitch4j.events.event.irc.roomstates.SlowModeEvent;
import me.philippheuer.twitch4j.events.event.irc.roomstates.SubscribersOnlyEvent;
import me.philippheuer.twitch4j.model.User;

@Setter(AccessLevel.PROTECTED)
@Getter(AccessLevel.PUBLIC)
public class ChannelCache {
	/**
	 * Twitch Chat
	 */
	private final TwitchChat chat;

	/**
	 * Event Manager
	 */
	private final EventManager eventManager;

	/**
	 * The channel this cache instance is for
	 */
	private final String channel;

	/**
	 * Moderators (triggered by {@link ChannelModEvent} aka <b>MODE</b> command)
	 */
	private final List<User> moderators = new ArrayList<User>();

	/*
	 * TODO: Mark deprecated - explanation below
	 * We don't needed caching timeouts and bans.
	 * If the bot will restarting, caches will be lost. Optional (caching in JDBC or file)
	 * There is no API endpoint for listing bans and timeouts (expected getting information about banned chatters via IRC)
	 *
	 * Delete this comment if you choose something (D.S.)
	 */

	/**
	 * Timeouts
	 */
	private final List<UserTimeoutEvent> timeoutEvents = new CopyOnWriteArrayList<UserTimeoutEvent>();

	/**
	 * Bans
	 */
	private final List<UserBanEvent> banEvents = new CopyOnWriteArrayList<UserBanEvent>();

	/**
	 * Channel States
	 */
	private final Map<ChannelStateEvent.ChannelState, Object> channelState = new HashMap<>();

	/**
	 * Channel Cache
	 *
	 * @param chat TwitchChat Instance
	 * @param channel The channel this cache is for
	 */
	public ChannelCache(TwitchChat chat, String channel) {
		this.chat = chat;
		this.channel = channel;
		this.eventManager = chat.getTwitchClient().getEventManager();

		// event consumers
		getEventManager().onEvent(ChannelModEvent.class).subscribe(event -> onChannelModStatusChange(event));
		getEventManager().onEvent(UserTimeoutEvent.class).subscribe(event -> onChannelTimeout(event));
		getEventManager().onEvent(UserBanEvent.class).subscribe(event -> onChannelBan(event));
		getEventManager().onEvent(ChannelStateEvent.class).subscribe(event -> onChannelState(event));
	}

	/**
	 * Mod Grant/Removed (also caching mods after join channel)
	 */
	public void onChannelModStatusChange(ChannelModEvent event) {
		if(event.getChannel().getName().equals(getChannel())) {
			// Add or remove moderator from cache
			if(event.isMod()) {
				if(!getModerators().contains(event.getUser())) {
					getModerators().add(event.getUser());
				}
			} else {
				getModerators().remove(event.getUser());
			}
		}
	}

	/**
	 * Timeout
	 */
	public void onChannelTimeout(UserTimeoutEvent event) {
		// Remove expired Events
		for (UserTimeoutEvent timeout : getTimeoutEvents()) {
			if (timeout.getFiredAt().getTimeInMillis() + timeout.getDuration() * 1000 < Calendar.getInstance().getTimeInMillis()) {
				// Expired
				synchronized (timeoutEvents) {
					getTimeoutEvents().remove(timeout);
				}
			}
		}

		// Add to Cache
		if(!isTimeoutCached(event)) {
			synchronized (timeoutEvents) {
				getTimeoutEvents().add(event);
			}
		}
	}

	/**
	 * Ban
	 */
	public void onChannelBan(UserBanEvent event) {
		// Add to Cache
		if(!isBanCached(event)) {
			getBanEvents().add(event);
		}
	}

	/**
	 * Channel State
	 */
	public void onChannelState(ChannelStateEvent event) {
		if (event.getStates().size() > 1) {
			channelState.putAll(event.getStates());
		} else if (event.getStates().size() == 1) {
			event.getStates().forEach((k, v) -> {
				if (!channelState.get(k).equals(v)) { // if state changes
					channelState.replace(k, v); // replacing
					switch (k) { // and emit event
						case BROADCAST_LANG:
							chat.getTwitchClient().getEventManager().dispatchEvent(new BroadcasterLanguageEvent(event.getChannel(), (Locale) v));
							break;
						case R9K:
							chat.getTwitchClient().getEventManager().dispatchEvent(new Robot9000Event(event.getChannel(), (Boolean) v));
							break;
						case SLOW:
							chat.getTwitchClient().getEventManager().dispatchEvent(new SlowModeEvent(event.getChannel(), (Long) v));
							break;
						case EMOTE:
							chat.getTwitchClient().getEventManager().dispatchEvent(new EmoteOnlyEvent(event.getChannel(), (Boolean) v));
							break;
						case FOLLOWERS:
							chat.getTwitchClient().getEventManager().dispatchEvent(new FollowersOnlyEvent(event.getChannel(), (Long) v));
							break;
						case SUBSCRIBERS:
							chat.getTwitchClient().getEventManager().dispatchEvent(new SubscribersOnlyEvent(event.getChannel(), (Boolean) v));
							break;
						default:
							break;
					}
				}
			});
		}
	}

	/**
	 * Checks if the TimeoutEvent is in the ChannelCache
	 *
	 * @param event The UserTimeoutEvent
	 * @return Boolean
	 */
	public Boolean isTimeoutCached(UserTimeoutEvent event) {
		//return timeoutEvents.contains(event); // You can use it
		for(UserTimeoutEvent timeout : getTimeoutEvents()) {
			if(timeout.getUser().getId().equals(event.getUser().getId())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Checks if the BanEvent is in the ChannelCache
	 *
	 * @param event The UserBanEvent
	 * @return Boolean
	 */
	public Boolean isBanCached(UserBanEvent event) {
		//return banEvents.contains(event); // You can use it
		for(UserBanEvent ban : getBanEvents()) {
			if(ban.getUser().getId().equals(event.getUser().getId())) {
				return true;
			}
		}

		return false;
	}
}