package net.benjaminurquhart.stealthrock.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GetEmUp extends ListenerAdapter {
	
	public static final String[] GIFS = {
			"https://tenor.com/view/gabite-gabite-uses-stealth-rock-pokemon-gabite-pokemon-gabite-uses-stealth-rock-gif-24862084",
			"https://tenor.com/view/stealth-rocks-stealth-rock-pokemon-pokemon-memes-pokemon-competitive-gif-13567735627639936536",
			"https://tenor.com/view/monotart-omori-kel-kel-omori-omori-kel-gif-24981194",
			"https://tenor.com/view/draje-rocks-stealth-rocks-spikes-forretress-gif-16557767097660400395",
			"https://tenor.com/view/great-tusk-heatran-smogon-stealth-rocks-willem-dafoe-gif-10692369271052617162"
	};
	
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if(!event.getName().equals("getemup")) {
			return;
		}
		
		// Lock to idiots (hopefully)
		Guild guild = event.getGuild();
		if(guild.getIdLong() == 347856291252666369L) {
			Role idiot = guild.getRoleById("473613101430996993");
			if(!event.getMember().getRoles().contains(idiot)) {
				event.reply("You cannot use this command.").setEphemeral(true).queue();
				return;
			}
		}
		
		event.reply(GIFS[(int)(Math.random() * GIFS.length)]).setEphemeral(false).queue();
	}
}
