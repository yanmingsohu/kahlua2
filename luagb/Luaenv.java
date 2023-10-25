import se.krka.kahlua.integration.annotations.LuaMethod;
import se.krka.kahlua.j2se.LuaRuntime;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm2.DebugInf;
import se.krka.kahlua.vm2.Tool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;


public class Luaenv extends LuaRuntime {

	private JDialog fr;
	private BufferedImage buf;
	private double frame;
	private double lastt;
	private double total = 1;
	private StringBuilder fps = createFPS();

	private final int Width = 500;
	private final int Height = 400;
	private final int GBw = 160;
	private final int GBh = 140;
	private int x = 200;
	private int y = 200;

	private static int count = 1;


	public static void main(String[] args) throws Throwable {
		Luaenv luaNew = new Luaenv(true, "./luagb/");
		luaNew.runOnThread("gb");

		Luaenv luaOld = new Luaenv(false, "./luagb/");
		luaOld.runOnThread("gb");
	}

	private Luaenv(boolean useNewVersion, String baseDir) {
		super(useNewVersion, baseDir);
		x += count * Width; count++;

		buf = new BufferedImage(Width, Height, TYPE_INT_RGB);
		lastt = System.currentTimeMillis();

		fr = new Screen();
		fr.setSize(Width,Height);
		fr.setBackground(Color.black);
		fr.setVisible(true);
	}


	public int debug() {
		return DebugInf.STATISTICS | DebugInf.BUILD;
	}


	@Override
	public void onExit() throws IOException {
		fr.dispose();
    Tool.pl("Done");
	}


	@Override
	public void onStart() throws IOException {
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
			//if (row == null) continue;

			for (int x=0; x < row.len(); ++x) {
				KahluaTable c = (KahluaTable) row.rawget(x);
				//if (c == null) continue;

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
			setFrame((int)f, (int)used);
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


	private void setFrame(int frame, int used) {
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

		fps.append(num2(1.0/(used/1000.0)));
		fps.append(" FPS");
		fps.append(sp);

		if (total > 100) {
			fps.append(num2(frame / (total / 1000)));
			fps.append(" FPS.avg");
			fps.append(sp);
		}
	}


	private static String num2(double x) {
		int r = (int)(x * 100.0);
		return (((double)r) / 100.0) +"";
	}


	private StringBuilder createFPS() {
		StringBuilder b = new StringBuilder();
		//     0->4321  10->4321 19->4321
		b.append("   0Frame    0ms      0FPS");
		return b;
	}


	public class Screen extends JDialog {
		public Screen() {
			setTitle("LuaGB on Kahlua - "+ thread.getClass().getSimpleName());
			addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					dispose();
				}
			});
			setLocation(x, y);
		}

		public void paint(Graphics g) {
			g.setColor(Color.black);
			//g.fill3DRect(0, 0, 500, 500, false);
			g.drawImage(buf, 0,0, null);
		}
	}
}
