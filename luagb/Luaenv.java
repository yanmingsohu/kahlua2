import se.krka.kahlua.converter.KahluaConverterManager;
import se.krka.kahlua.integration.LuaCaller;
import se.krka.kahlua.integration.LuaReturn;
import se.krka.kahlua.integration.annotations.LuaMethod;
import se.krka.kahlua.integration.expose.LuaJavaClassExposer;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaThread;
import se.krka.kahlua.vm.LuaClosure;
import se.krka.kahlua.vm2.DebugInf;
import se.krka.kahlua.vm2.KahluaThread2;
import se.krka.kahlua.vm2.Tool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;


public class Luaenv {

	private final J2SEPlatform platform;
	private final KahluaTable env;
	private final KahluaThread thread;
	private final LuaCaller caller;
	private final LuaJavaClassExposer exposer;
	private final String baseDir = "./luagb/";

	private Map<String, Object> libCache;
	private JDialog fr;
	private BufferedImage buf;
	private double frame;
	private double lastt;
	private double total;
	private StringBuilder fps = createFPS();

	private final int Width = 500;
	private final int Height = 400;
	private final int GBw = 160;
	private final int GBh = 140;
	private final int DEBUG_FLAG = DebugInf.ALL;


	public static void main(String[] args) throws Throwable {
		Luaenv lua = new Luaenv();
		lua.run();
	}

	private KahluaThread createThread(boolean newT) {
		if (newT) {
			KahluaThread2 t2 = new KahluaThread2(platform, env);
			t2.setDebug(DEBUG_FLAG);
			t2.setOutputDir("./bin/lua");
			return t2;
		} else {
			return new KahluaThread(platform, env);
		}
	}

	private Luaenv() {
		KahluaConverterManager converterManager = new KahluaConverterManager();
		platform = new J2SEPlatform();
		env = platform.newEnvironment();
		thread = createThread(true);
		caller = new LuaCaller(converterManager);
		exposer = new LuaJavaClassExposer(converterManager, platform, env);
		//thread.setOutputDir("./bin");

		libCache = new HashMap<>();
		buf = new BufferedImage(Width, Height, TYPE_INT_RGB);
		lastt = System.currentTimeMillis();

		fr = new Screen();
		fr.setSize(Width,Height);
		fr.setBackground(Color.black);
		fr.setVisible(true);
	}

	public void run() throws IOException {
		// java bit
		KahluaTable javaBase = platform.newEnvironment();
		// lua bit
		//KahluaTable javaBase = platform.newTable();

		env.rawset("Java", javaBase);
		exposer.exposeLikeJavaRecursively(ArrayList.class, javaBase);
		exposer.exposeGlobalFunctions(this);


		try {
			require("gb");
			fr.dispose();
		} catch (Exception e) {
			e.printStackTrace();
			printdebug();
			Tool.printTable(env);
		}
		pl("Done");
	}


	public Throwable finderror(Object[] retObj) {
		for (int i=0; i<retObj.length; ++i) {
			Tool.pl(i, "->", retObj[i]);
			if (retObj[i] instanceof Throwable) {
				return (Throwable) retObj[i];
			}
		}
		return null;
	}


	@LuaMethod(name = "require", global = true)
	public Object require(String name) throws IOException {
		final String filename = baseDir + name + ".lua";
		Object lib = libCache.get(filename);
		if (lib != null) {
			return lib;
		}

		FileInputStream fi = new FileInputStream(filename);

		LuaClosure closure = LuaCompiler.loadis(fi, filename, env);
		Object[] retObj = caller.pcall(thread, closure);
		LuaReturn ret = LuaReturn.createReturn(retObj);
		if (!ret.isSuccess()) {
			Throwable t = finderror(retObj);
			if (t == null) {
				throw new RuntimeException(ret.toString());
			} else {
				throw new RuntimeException(t);
			}
		}

		if (ret.size() > 0) {
			lib = ret.get(0);
		} else {
			lib = 0;
		}
		libCache.put(filename, lib);
		return lib;
	}


	@LuaMethod(name = "updateScreen", global = true)
	public void updateScreen(KahluaTable pixel) {
		Graphics gr = buf.getGraphics();
		gr.setColor(Color.black);
		gr.fillRect(0,0, Width,Height);
		final int offx = (Width-GBw*2) / 2;
		final int offy = (Height - GBh*2) / 2;


		for (int y = 0; y < pixel.len(); ++y) {
			KahluaTable row = (KahluaTable) pixel.rawget(y);

			for (int x=0; x < row.len(); ++x) {
				KahluaTable c = (KahluaTable) row.rawget(x);

				int r = (int)(double) c.rawget(1);
				int g = (int)(double) c.rawget(2);
				int b = (int)(double) c.rawget(3);
				int rgb = (r << 16) | (g << 8) | b;
				buf.setRGB(x*2   + offx, y*2   + offy, rgb);
				buf.setRGB(x*2   + offx, y*2+1 + offy, rgb);
				buf.setRGB(x*2+1 + offx, y*2   + offy, rgb);
				buf.setRGB(x*2+1 + offx, y*2+1 + offy, rgb);
			}
		}

		gr.setColor(Color.white);
		gr.drawString(fps.toString(), offx, 50);
		fr.repaint();
	}


	@LuaMethod(name = "setFrame", global = true)
	public void setFrame(double f) {
		if (frame != f) {
			double now = System.currentTimeMillis();
			double used = now - lastt;
			lastt = now;
			setFrame((int)f, (int)used, (int)(1/(used/1000)));
			total += used;
		}
		frame = f;
	}


	@LuaMethod(name = "loadRom", global = true)
	public void loadRom(KahluaTable card, String filename) {
		KahluaTable data = (KahluaTable) card.rawget("data");
		try (FileInputStream in = new FileInputStream(filename)) {
			int i = 0;
			int d = 0;

			for (; ; ) {
				d = in.read();
				if (d < 0) {
					break;
				}
				data.rawset(Double.valueOf(i+1), Double.valueOf(d));
				++i;
			}
			card.rawset("size", Double.valueOf(i));
			Tool.pl("Load file", filename, i, "bytes");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	private void setFrame(int frame, int used, int _fps) {
		final String sp = "   ";
		fps.setLength(0);
		fps.append(frame);
		fps.append(" Frame");
		fps.append(sp);

		if (used > 1000) {
			fps.append(used/1000);
			fps.append(" s");
		} else {
			fps.append(used);
			fps.append(" ms");
		}
		fps.append(sp);

		fps.append(_fps);
		fps.append(" FPS");
		fps.append(sp);

		fps.append((int)(frame / (total/1000)));
		fps.append(" FPS.avg");
	}


	private StringBuilder createFPS() {
		StringBuilder b = new StringBuilder();
		//     0->4321  10->4321 19->4321
		b.append("   0Frame    0ms      0FPS");
		return b;
	}


	public void printdebug() {
		if (thread instanceof KahluaThread2) {
			KahluaThread2 t2 = (KahluaThread2) thread;
			t2.printStack();
		}
	}


	public static void pl(Object ...o) {
		StringBuilder buf = new StringBuilder();
		for (int i=0; i<o.length; ++i) {
			buf.append(o[i]);
			buf.append(" ");
		}
		System.out.println(buf.toString());
	}


	public class Screen extends JDialog {
		public Screen() {
			setTitle("LuaGB on Kahlua");
			addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					dispose();
				}
			});
		}

		public void paint(Graphics g) {
			g.setColor(Color.black);
			//g.fill3DRect(0, 0, 500, 500, false);
			g.drawImage(buf, 0,0, null);
		}
	}
}
