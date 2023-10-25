/*
 Copyright (c) 2023 Yanming <yanmingsohu@gmail.com>

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */

import se.krka.kahlua.j2se.LuaRuntime;
import se.krka.kahlua.vm.*;
import se.krka.kahlua.vm2.Tool;

import java.io.IOException;


/**
 * Donload benchmarks from:
 * https://github.com/gligneul/Lua-Benchmarks
 */
public class Benchmarks extends LuaRuntime {

  public final static String baseDir = "./Lua-Benchmarks/";

  private long start;
  private long used;


  public static void main(String[] avg) throws Exception {
//    t("ack"); // Stack overflow
//    t("fasta"); // Stack overflow

//    t("k-nucleotide"); // io.lines()
//    t("regex-dna"); // io.read
//    t("scimark"); // unpack()

    t("mandel");
    t("fixpoint-fact");
    t("binary-trees");
    t("fannkuch-redux");
    t("heapsort");
    t("qt");
    t("n-body");
    t("queen");
    t("sieve");
    t("spectral-norm");
  }


  public static void t(String name) throws Exception {
    Benchmarks bNew = new Benchmarks(true);
    bNew.runOnThread(name).join();

    Benchmarks bOld = new Benchmarks(false);
    bOld.runOnThread(name).join();

    Tool.pl("Benchmarks >>", name, "\n",
      bNew, "\tUsed", bNew.usedTime(), "ms\n",
      bOld, "\tUsed", bOld.usedTime(), "ms\n"
    );
  }


  public Benchmarks(boolean newVersion) {
    super(newVersion, baseDir);
    KahluaTable io = super.platform.newTable();
    super.env.rawset("io", io);
    io.rawset("write", new Write());
    KahluaTable stdout = super.platform.newTable();
    io.rawset("stdout", stdout);
    stdout.rawset("write", new Write());

    KahluaTable os = super.platform.newTable();
    super.env.rawset("os", os);
    os.rawset("clock", new Clock());
    os.rawset("exit", new Exit());

    KahluaTable arg = super.platform.newTable();
    arg.rawset(1, Double.valueOf(10));
    super.env.rawset("arg", arg);
    super.env.rawset("tonumber", new Tonumber());
    super.env.rawset("unpack", new Unpack());
  }


  public class Exit implements JavaFunction {

    @Override
    public int call(LuaCallFrame callFrame, int nArguments) {
      return 0;
    }
  }


  public class Unpack implements JavaFunction {

    @Override
    public int call(LuaCallFrame callFrame, int nArguments) {
      int i = 0;
      KahluaTable t = (KahluaTable) callFrame.get(0);
      for (;;) {
        Object v = t.rawget(i);
        if (v == null) break;
        ++i;
        callFrame.push(v);
      }
      return i;
    }
  }


  public class Write implements JavaFunction {

    @Override
    public int call(LuaCallFrame callFrame, int nArguments) {
      StringBuilder buf = new StringBuilder();
      for (int i=0; i<nArguments; ++i) {
        buf.append(callFrame.get(i));
      }
      System.out.print(buf);
      return 0;
    }
  }


  public class Donothing implements JavaFunction {

    @Override
    public int call(LuaCallFrame callFrame, int nArguments) {
      return 0;
    }
  }


  public class Tonumber implements JavaFunction {

    @Override
    public int call(LuaCallFrame callFrame, int nArguments) {
      Double d = KahluaUtil.rawTonumber(callFrame.get(0));
      callFrame.push(d);
      return 1;
    }
  }


  public class Clock implements JavaFunction {

    @Override
    public int call(LuaCallFrame callFrame, int nArguments) {
      callFrame.push(KahluaUtil.toDouble(System.currentTimeMillis() / 1000));
      return 1;
    }
  }


  @Override
  public void onExit() throws IOException {
    used = System.currentTimeMillis() - start;
  }


  long usedTime() {
    return used;
  }


  @Override
  public void onStart() throws IOException {
    start = System.currentTimeMillis();
  }


  public String toString() {
    return super.thread.getClass().getSimpleName();
  }
}
