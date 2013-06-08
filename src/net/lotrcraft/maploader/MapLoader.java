package net.lotrcraft.maploader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MapLoader extends JavaPlugin {

	static Logger log;
	static Loader loader;
	
	public void onLoad(){
		log = getLogger();
	}
	
	public void onDisable(){
		if(loader != null)
			loader.terminate();
		log.info("MapLoader disabled!");
	}

	public void onEnable() {
		try {
			Metrics metrics = new Metrics(this);
			metrics.start();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		log.info("MapLoader enabled!");
	}

	public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args) {

		int r, x, z;
		World w;
		Location l;

		if (sender instanceof ConsoleCommandSender) {

			if (command.getName().equals("terminate")) {

				if (loader == null) {
					log.info("Nothing to terminate!");
				} else {
					loader.terminate();
					loader = null;
					log.info("Current loader terminated.");
				}

				return true;

			} if (command.getName().equals("load") || command.getName().equals("hyperload")) {
				
				if (args.length == 0)
					return false;
				
				if(loader != null)
					log.info("Already loading!");
				
				w = Bukkit.getWorld(args[0]);
				if (w == null) {
					log.info("Invalid world!");
					return true;
				}

				try {
					r = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {
					log.info("Invalid radius!");
					return false;
				}

				if (args.length <= 2) {
					log.info("Using spawn as center");
					x = w.getSpawnLocation().getBlockX();
					z = w.getSpawnLocation().getBlockZ();
				} else if (args.length >= 4) {
					try {
						x = Integer.parseInt(args[2]);
						z = Integer.parseInt(args[3]);
					} catch (NumberFormatException e) {
						log.info("Invalid Points!");
						return false;
					}
				} else {
					log.info("Not enough arguments!");
					return false;
				}
				
			} else return false;

		} else {

			if (command.getName().equals("terminate")) {
				
				if (loader == null) {
					sender.sendMessage(ChatColor.GRAY + "Nothing to terminate!");
				} else {
					loader.terminate();
					loader = null;
					sender.sendMessage(ChatColor.DARK_PURPLE + "Current loader terminated.");
				}

				return true;

			} else if (command.getName().equals("load") || command.getName().equals("hyperload")) {
				
				if (args.length == 0)
					return false;
				
				if(loader != null)
					sender.sendMessage("Already loading!");
				
				if (!sender.hasPermission("ml.load")) {
					sender.sendMessage(ChatColor.DARK_RED + "You may not do this!");
					return true;
				}

				w = Bukkit.getWorld(args[0]);
				if (w == null) {
					sender.sendMessage(ChatColor.RED + "Invalid world!");
					return true;
				}

				try {
					r = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {
					sender.sendMessage(ChatColor.RED + "Invalid radius!");
					return false;
				}

				if (args.length <= 2) {
					sender.sendMessage(ChatColor.GREEN + "Using your location as center");
					x = this.getServer().getPlayer(sender.getName())
							.getLocation().getBlockX();
					z = this.getServer().getPlayer(sender.getName())
							.getLocation().getBlockZ();
				} else if (args.length >= 4) {
					try {
						x = Integer.parseInt(args[2]);
						z = Integer.parseInt(args[3]);
					} catch (NumberFormatException e) {
						sender.sendMessage(ChatColor.RED + "Invalid Points!");
						return false;
					}
				} else {
					return false;
				}
				
			} else return false;

		}

		l = new Location(w, x, 0, z);
		if (getServer().getPluginManager().getPlugin("dynmap") != null)
			getServer().getPluginManager().disablePlugin(
					getServer().getPluginManager().getPlugin("dynmap"));
		getServer().broadcastMessage(ChatColor.AQUA + "Loading " + (r + r) * (r + r)
				+ " chunks from X:" + l.getBlockX() + " and Z:" + l.getBlockZ());
		if(command.getName().equals("hyperload"))
			getServer().broadcastMessage(ChatColor.GOLD + "Warp Speed, Mr. Sulu.");
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		for(Player p : getServer().getOnlinePlayers())
			p.kickPlayer(ChatColor.BLUE + "Hypermode has been Activated! Please check back in a bit.");
		
		w.save();
		
		loader = command.getName().equals("hyperload") ? new HyperLoader(l, r, this) : new Loader(l, r, this) ;
		loader.start();

		return true;
	}

	private class Loader extends Thread {

		protected boolean terminate;
		protected Location l;
		protected int r;
		private MapLoader ml;

		public Loader(Location l, int r, MapLoader ml) {
			this.l = l;
			this.r = r;
			this.ml = ml;
		}

		public void terminate() {
			terminate = true;
		}

		@Override
		public void run() {
			long time = System.currentTimeMillis();
			Runtime rt = Runtime.getRuntime();

			terminate = false;
			int cnt = 0;
			long memUsed;

			try {
				for (int x = -r; x < r; x++) {
					long freeMem = rt.freeMemory() / 1024;
					
					List<ChunkLoader> cls = Collections.synchronizedList(new ArrayList<ChunkLoader>());
					
					for (int z = -r; z < r; z++) {

							if (terminate)
								break;
							
							ChunkLoader cl = new ChunkLoader(x, z, l, cls);
							cls.add(cl);
							
							Bukkit.getScheduler().scheduleSyncDelayedTask(ml, cl);
							cnt++;
							
							Thread.sleep(4);
					}
					
					Thread.sleep(1000);
					
					while(!cls.isEmpty()){
						
						if (terminate)
							break;
						
						log.info("Waiting for system to catch up...");
						Thread.sleep(1000);
					}
					
					log.info("");

					if (terminate)
						break;

					memUsed = freeMem - rt.freeMemory() / 1024;
					log.info("Loaded " + cnt + " of " + (r+r)*(r+r) + " chunks.");
					log.info("Memory used: " + memUsed + " kb, per chunk: " + memUsed / (2 * r) + " kb");
					freeMem = rt.freeMemory() / 1024;
					log.info("Memory left: "	+ freeMem + " kb");
					log.info("Starting garbage collection... ");
					
					rt.gc();
					
					long limit = memUsed > 20000 ? memUsed : 20000;
					
					while (rt.freeMemory() / 1024 < limit && !terminate){
						log.info("Out of memory, waiting...");
						Thread.sleep(10000);
						rt.gc();
					}
					
					log.info("Memory freed: " + (rt.freeMemory() / 1024 - freeMem) + "kb");

				}

			} catch (Exception e) {
				log.severe("Unexpected error: " + e.getMessage());
				e.printStackTrace();
			} finally {

				Bukkit.broadcastMessage("Finished loading chunks, took " + (System.currentTimeMillis() - time) / 60000.0 + " minutes ");
				if (getServer().getPluginManager().getPlugin("dynmap") != null)
					getServer().getPluginManager().enablePlugin(
							getServer().getPluginManager().getPlugin("dynmap"));
				l.getWorld().save();
				
				loader = null;
			}
		}

	}
	
	private class HyperLoader extends Loader {

		public HyperLoader(Location l, int r, MapLoader ml) {
			super(l, r, ml);
		}

		@Override
		public void run() {
			long time = System.currentTimeMillis();
			Runtime rt = Runtime.getRuntime();

			terminate = false;
			int cnt = 0;
			int xLoc, zLoc;
			long memUsed;

			try {
				long freeMem = rt.freeMemory() / 1024;
				for (int x = -r; x < r; x++) {
					
					synchronized (l.getWorld()){
						for (int z = -r; z < r; z++) {

							if (terminate)
								break;

							xLoc = l.getChunk().getX() + x;
							zLoc = l.getChunk().getZ() + z;
							cnt++;
						
						
							l.getWorld().loadChunk(xLoc, zLoc);
							l.getWorld().unloadChunk(xLoc, zLoc, true);
							
							Thread.sleep(1);
							
							
							if(rt.freeMemory() / 1024 < 200000){
								memUsed = freeMem - rt.freeMemory() / 1024;
								log.info("");
								log.info("Loaded " + cnt + " of " + (r+r)*(r+r) + " chunks.");
								log.info("Memory used: " + memUsed + " kb, per chunk: " + memUsed / (2 * r) + " kb");
								log.info("Starting garbage collection... ");
								
								Thread.sleep(5000);
								rt.gc();
								log.info("Memory freed: " + (rt.freeMemory() / 1024 - freeMem) + "kb");
								
								freeMem = rt.freeMemory() / 1024;
							}
							
							
							

						}
						
						log.info("Memory left: "	+ rt.freeMemory() / 1024 + " kb. On chunk " + cnt);
						
						if (terminate)
							break;
					}
					

				}

			} catch (Exception e) {
				log.severe("Unexpected error: " + e.getMessage());
				e.printStackTrace();
			} finally {

				Bukkit.broadcastMessage("Finished loading chunks, took " + (System.currentTimeMillis() - time) / 60000.0 + " minutes ");
				if (getServer().getPluginManager().getPlugin("dynmap") != null)
					getServer().getPluginManager().enablePlugin(
							getServer().getPluginManager().getPlugin("dynmap"));
				l.getWorld().save();
				
				loader = null;
			}
		}

	}
	
	private class ChunkLoader implements Runnable{
		
		private Location l;
		private int x;
		private int z;
		private List<ChunkLoader> cls;

		public ChunkLoader(int x, int z, Location l, List<ChunkLoader> cls2){
			this.cls = cls2;
			this.x = x;
			this.z = z;
			this.l = l;
		}

		@Override
		public void run() {
			int xLoc = l.getChunk().getX() + x;
			int zLoc = l.getChunk().getZ() + z;
			
			l.getWorld().loadChunk(xLoc, zLoc);
			l.getWorld().unloadChunk(xLoc, zLoc, true);
			
			cls.remove(this);
			
		}
	}
	
	private String concatWorldName(String[] args, int start){
		
		String tmp = args[start];
		
		//The first char isnt a quote
		if(tmp.charAt(0) != '"')
			return tmp;
		
		//There are multiple quotes in one arg
		if(tmp.lastIndexOf('"') != 0)
			return tmp;
		
		int end = -1;
		for(int y = start + 1; y < args.length; y++){
			tmp = tmp + args[y];
			if(args[y].indexOf('"') != -1){
				end = y;
				break;
			}
		}
		
		if(end == -1)
			return args[start];
		
		for(int y = start + 1; y <= end; y++)
			remove(args, y);
		
		return args[start] = tmp.replaceAll("\"", "");
		
	}
	
	private void remove(String[] s, int i){
		for(int y = i; y < s.length - 1; y++)
			s[y] = s[y+1];
		s[s.length - 1] = "";
	}

}
