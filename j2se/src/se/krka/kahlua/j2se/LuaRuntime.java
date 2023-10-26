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

package se.krka.kahlua.j2se;

import se.krka.kahlua.converter.KahluaConverterManager;
import se.krka.kahlua.integration.LuaCaller;
import se.krka.kahlua.integration.LuaReturn;
import se.krka.kahlua.integration.annotations.LuaMethod;
import se.krka.kahlua.integration.expose.LuaJavaClassExposer;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaThread;
import se.krka.kahlua.vm.LuaClosure;
import se.krka.kahlua.vm.Platform;
import se.krka.kahlua.vm2.DebugInf;
import se.krka.kahlua.vm2.KahluaThread2;
import se.krka.kahlua.vm2.Tool;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;


public abstract class LuaRuntime {

  protected final Platform platform;
  protected final KahluaTable env;
  protected final KahluaThread thread;
  protected final LuaCaller caller;
  protected final LuaJavaClassExposer exposer;
  protected final String baseDir;

  protected final Map<String, Object> libCache;
  protected boolean newVersion;
  protected String lastName;
  protected LuaPrintStream out;


  public LuaRuntime(boolean useNewVersion, String baseDir) {
    this.out = new LuaPrintStream(System.out);
    this.newVersion = useNewVersion;
    this.baseDir = baseDir;
    KahluaConverterManager converterManager = new KahluaConverterManager();
    platform = getPlatform();
    env = getEnv();
    thread = createThread();
    caller = new LuaCaller(converterManager);
    exposer = new LuaJavaClassExposer(converterManager, platform, env);
    libCache = new HashMap<>();
  }


  protected Platform getPlatform() {
    if (newVersion) {
      return new J2SEPlatform2();
    } else {
      return new J2SEPlatform();
    }
  }


  protected KahluaTable getEnv() {
    if (newVersion) {
      J2SEPlatform2 v2 = (J2SEPlatform2) platform;
      v2.useConcurrent(false);
      return v2.newEnvironment();
    } else {
      return platform.newEnvironment();
    }
  }


  protected KahluaThread createThread() {
    if (newVersion) {
      KahluaThread2 t2 = new KahluaThread2(out, platform, env);
      t2.setDebug(debug());
      t2.setOutputDir("./bin/lua");
      return t2;
    } else {
      return new KahluaThread(out, platform, env);
    }
  }


  public int debug() {
    return DebugInf.BUILD;
  }


  public abstract void onExit() throws IOException;


  public abstract void onStart() throws IOException;


  public Thread runOnThread(String name) {
    Thread t = new Thread(()-> {
      try {
        run(name);
      } catch(Exception e) {
        e.printStackTrace();
      }
    });
    t.start();
    return t;
  }


  public void run(String name) throws IOException {
    exposer.exposeGlobalFunctions(this);

    try {
      onStart();
      require(name);
      onExit();
    } catch (Exception e) {
      e.printStackTrace();
      printdebug();
      Tool.printTable(env);
    }
  }


  public void printdebug() {
    if (thread instanceof KahluaThread2) {
      KahluaThread2 t2 = (KahluaThread2) thread;
      t2.printStack();
    }
  }


  @LuaMethod(name = "require", global = true)
  public Object require(String name) throws IOException {
    lastName = name;
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


  public Throwable finderror(Object[] retObj) {
    for (int i=0; i<retObj.length; ++i) {
      Tool.pl(i, "->", retObj[i]);
      if (retObj[i] instanceof Throwable) {
        return (Throwable) retObj[i];
      }
    }
    return null;
  }


  public class LuaPrintStream extends PrintStream {
    public boolean open = true;

    public LuaPrintStream(OutputStream out) {
      super(out);
    }

    public void println(String x) {
      if (open) {
        super.println(x);
      }
    }
  }
}
