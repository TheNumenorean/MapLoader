package net.lotrcraft.maploader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
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

	private static final int DEFAULT_LOAD_SIZE = 100;

	@Override
	public void onLoad() {
		log = getLogger();
	}

	@Override
	public void onDisable() {
		if (loader != null)
			loader.terminate();
		log.info("MapLoader disabled!");
	}

	@Override
	public void onEnable() {
		log.info("MapLoader enabled!");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

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

			}
			if (command.getName().equals("load") || command.getName().equals("hyperload")) {

				if (args.length == 0)
					return false;

				if (loader != null)
					log.info("Already loading!");

				args = concatWorldName(args, 0);

				if (args.length < 2)
					return false;

				w = Bukkit.getWorld(args[0]);
				if (w == null) {
					log.info("Invalid world " + args[0] + "!");
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

			} else
				return false;

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

				if (loader != null)
					sender.sendMessage("Already loading!");

				if (!sender.hasPermission("ml.load")) {
					sender.sendMessage(ChatColor.DARK_RED + "You may not do this!");
					return true;
				}

				args = concatWorldName(args, 0);

				if (args.length < 2)
					return false;

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
					x = this.getServer().getPlayer(sender.getName()).getLocation().getBlockX();
					z = this.getServer().getPlayer(sender.getName()).getLocation().getBlockZ();
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

			} else
				return false;

		}

		l = new Location(w, x, 0, z);
		if (getServer().getPluginManager().getPlugin("dynmap") != null)
			getServer().getPluginManager().disablePlugin(getServer().getPluginManager().getPlugin("dynmap"));
		getServer().broadcastMessage(ChatColor.AQUA + "Loading " + (r + r) * (r + r) + " chunks from X:" + l.getBlockX() + " and Z:" + l.getBlockZ());
		if (command.getName().equals("hyperload"))
			getServer().broadcastMessage(ChatColor.GOLD + "Warp Speed, Mr. Sulu.");

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if (command.getName().equals("hyperload"))
			for (Player p : getServer().getOnlinePlayers())
				p.kickPlayer(ChatColor.BLUE + "Hypermode has been Activated! Please check back in a bit.");

		w.save();

		loader = command.getName().equals("hyperload") ? new HyperLoader(l, r, this, DEFAULT_LOAD_SIZE) : new Loader(l, r, this, DEFAULT_LOAD_SIZE);
		loader.start();

		return true;
	}

	private class Loader extends Thread {

		protected boolean terminate;
		protected Location l;
		protected int r;
		protected MapLoader ml;
		protected int size;

		public Loader(Location l, int r, MapLoader ml, int size) {
			this.l = l;
			this.r = r;
			this.ml = ml;
			this.size = size;
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

			try {
				List<ChunkLoader> current = Collections.synchronizedList(new ArrayList<ChunkLoader>());
				long freeMem = rt.freeMemory() / 1024;

				Queue<ChunkLoader> queue = new LinkedList<ChunkLoader>();

				for (int x = -r; x < r; x++) {
					for (int z = -r; z < r; z++) {
						if (terminate)
							break;
						queue.offer(new ChunkLoader(x, z, l, null));
					}
					if (terminate)
						break;
				}

				while (!queue.isEmpty()) {

					long memUsed;
					ChunkLoader cl;
					if (size < 20)
						size = 20;
					int amt = size;
					while (amt != 0 && (cl = queue.poll()) != null) {

						if (terminate)
							break;

						current.add(cl);
						cl.setList(current);
						Bukkit.getScheduler().scheduleSyncDelayedTask(ml, cl);
						cnt++;

						Thread.sleep(4);

						amt--;
					}

					Thread.sleep(1000);

					while (!current.isEmpty()) {

						if (terminate)
							break;

						log.info("Waiting for system to catch up, " + current.size() + " chunks left.");
						Thread.sleep(2000);
						rt.gc();
						size--;
					}

					log.info("");
					memUsed = freeMem - rt.freeMemory() / 1024;
					log.info("Loaded " + cnt + " of " + (r + r) * (r + r) + " chunks.");
					log.info("Memory used: " + memUsed + " kb, per chunk: " + memUsed / (size) + " kb");
					freeMem = rt.freeMemory() / 1024;
					log.info("Memory left: " + freeMem + " kb");
					log.info("Starting garbage collection... ");

					rt.gc();

					long limit = memUsed > 30000 ? memUsed : 30000;

					while (rt.freeMemory() / 1024 < limit && !terminate) {
						log.info("Out of memory, waiting...");
						size -= 2;
						Thread.sleep(10000);
						rt.gc();
					}

					log.info("Memory freed: " + (rt.freeMemory() / 1024 - freeMem) + "kb");

					if (freeMem > 200000) {
						if (memUsed < 5000)
							size++;
						else
							size--;
					} else {
						if (memUsed > 5000)
							size--;
					}

					if (terminate)
						break;

				}

			} catch (Exception e) {
				log.severe("Unexpected error: " + e.getMessage());
				e.printStackTrace();
			} finally {

				Bukkit.broadcastMessage("Finished loading chunks, took " + (System.currentTimeMillis() - time) / 60000.0 + " minutes ");

				Bukkit.getScheduler().scheduleSyncDelayedTask(ml, new Runnable() {

					@Override
					public void run() {
						l.getWorld().save();
					}

				});
				if (getServer().getPluginManager().getPlugin("dynmap") != null)
					getServer().getPluginManager().enablePlugin(getServer().getPluginManager().getPlugin("dynmap"));

				loader = null;
			}
		}

	}

	private class HyperLoader extends Loader {

		public HyperLoader(Location l, int r, MapLoader ml, int size) {
			super(l, r, ml, size);
		}

		@Override
		public void run() {

			List<ChunkLoader> cls = Collections.synchronizedList(new ArrayList<ChunkLoader>());
			long time = System.currentTimeMillis();
			Runtime rt = Runtime.getRuntime();

			terminate = false;
			int cnt = 0;
			long memUsed;

			try {
				long freeMem = rt.freeMemory() / 1024;
				for (int x = -r; x < r; x++) {

					for (int z = -r; z < r; z++) {
						if (terminate)
							break;

						ChunkLoader cl = new ChunkLoader(x, z, l, cls);
						cls.add(cl);

						Bukkit.getScheduler().scheduleSyncDelayedTask(ml, cl);
						cnt++;

						Thread.sleep(1);

						if (rt.freeMemory() / 1024 < 200000) {
							memUsed = freeMem - rt.freeMemory() / 1024;
							log.info("");
							log.info("Loaded " + cnt + " of " + (r + r) * (r + r) + " chunks.");
							log.info("Memory used: " + memUsed + " kb, per chunk: " + memUsed / (2 * r) + " kb");
							log.info("Starting garbage collection... ");

							Thread.sleep(5000);
							rt.gc();
							log.info("Memory freed: " + (rt.freeMemory() / 1024 - freeMem) + "kb");

							freeMem = rt.freeMemory() / 1024;
						}

					}

					log.info("Memory left: " + rt.freeMemory() / 1024 + " kb. On chunk " + cnt);

					if (terminate)
						break;

				}

			} catch (Exception e) {
				log.severe("Unexpected error: " + e.getMessage());
				e.printStackTrace();
			} finally {

				Bukkit.broadcastMessage("Finished loading chunks, took " + (System.currentTimeMillis() - time) / 60000.0 + " minutes ");
				if (getServer().getPluginManager().getPlugin("dynmap") != null)
					getServer().getPluginManager().enablePlugin(getServer().getPluginManager().getPlugin("dynmap"));
				Bukkit.getScheduler().scheduleSyncDelayedTask(ml, new Runnable() {

					@Override
					public void run() {
						l.getWorld().save();

					}

				});

				loader = null;
			}
		}

	}

	private class ChunkLoader implements Runnable {

		private final Location l;
		private final int x;
		private final int z;
		private List<ChunkLoader> cls;

		public ChunkLoader(int x, int z, Location l, List<ChunkLoader> cls2) {
			this.cls = cls2;
			this.x = x;
			this.z = z;
			this.l = l;
		}

		public void setList(List<ChunkLoader> list) {
			cls = list;
		}

		@Override
		public void run() {
			int xLoc = l.getChunk().getX() + x;
			int zLoc = l.getChunk().getZ() + z;

			l.getWorld().loadChunk(xLoc, zLoc);
			l.getWorld().unloadChunk(xLoc, zLoc, true);

			if (cls != null)
				cls.remove(this);

		}
	}

	private String[] concatWorldName(String[] args, int worldStartIndex) {

		String tmp = args[worldStartIndex];

		// The first char isnt a quote or There are multiple quotes in one arg
		if (tmp.charAt(0) != '"' || tmp.lastIndexOf('"') != 0) {
			args[worldStartIndex] = tmp.replaceAll("\"", "");
			return args;
		}

		int end = -1;
		for (int y = worldStartIndex + 1; y < args.length; y++) {
			tmp = tmp + " " + args[y];
			if (args[y].indexOf('"') != -1) {
				end = y;
				break;
			}
		}

		if (end == -1) {
			args[worldStartIndex] = args[worldStartIndex].replaceAll("\"", "");
			return args;
		}

		for (int y = worldStartIndex + 1; y <= end; y++)
			remove(args, y);

		args[worldStartIndex] = tmp.replaceAll("\"", "");

		args = Arrays.copyOf(args, args.length - (end - worldStartIndex));

		return args;

	}

	private void remove(String[] s, int i) {
		for (int y = i; y < s.length - 1; y++)
			s[y] = s[y + 1];
		s[s.length - 1] = "";
	}

}
