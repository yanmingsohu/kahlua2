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

import org.objectweb.asm.signature.SignatureWriter;
import se.krka.kahlua.converter.KahluaConverterManager;
import se.krka.kahlua.integration.LuaCaller;
import se.krka.kahlua.integration.LuaReturn;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.*;
import se.krka.kahlua.vm2.DebugInf;
import se.krka.kahlua.vm2.KahluaThread2;
import se.krka.kahlua.vm2.Tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;


public class TestKahluaThread2 implements Runnable {

  static final boolean USE_NEW_THREAD = true;
  static final int DEBUG = DebugInf.NONE;
  public LuaCallFrame callFrame;
  private static KahluaTable lastEnv;


  public void ___asm() {
  }


  public static void main(String[] av) throws Exception {
    TestVM tv = new TestVM();
    tv.testThrow("./testsuite/lua/throw.lua");
    tv.lua("./testsuite/lua/testhelper.lua");
    tv.lua("./testsuite/lua/forloop.lua");

    //TODO: org.objectweb.asm.MethodTooLargeException: Method too large
    // tv.lua("./luagb/cartridge/Tetris.gb.lua");

//    testAllLua();
//    test_signature();

    Tool.pl("Done");
  }


  private static void testAllLua() throws Exception {
    File dir = new File("./testsuite/lua");
//    File[] files = from(dir, "os.lua");
    File[] files = from(dir, ".lua");

    try {
      Test.testDir(dir, files);
    } catch (Exception e) {
      e.printStackTrace();
      Tool.printTable(lastEnv);
    }
  }


  private static File[] from(File dir, String ext) throws Exception {
    Predicate<Path> luafile = new Predicate<Path>() {
      public boolean test(Path o) {
        return o.toFile().getName().endsWith(ext);
      }
    };

    File[] ret;
    try (Stream<Path> s = Files.walk(dir.toPath(), 1)) {
      List<File> files = new ArrayList<>();

      s.filter(luafile).forEach((p)-> files.add(p.toFile()) );
      ret = files.toArray(new File[files.size()]);
    }
    return ret;
  }


  public static KahluaThread newThread() {
    Platform pl = J2SEPlatform.getInstance();
    PrintStream out = System.out;
    lastEnv = pl.newEnvironment();

    if (USE_NEW_THREAD) {
      KahluaThread2 thread = new KahluaThread2(out, pl, lastEnv);
      thread.setOutputDir("./bin/lua");
      thread.setDebug(DEBUG);
      return thread;
    } else {
      return new KahluaThread(out, pl, lastEnv);
    }
  }


  public static class TestVM {
    KahluaThread thread;
    LuaCaller caller;

    public TestVM() {
      KahluaConverterManager converterManager = new KahluaConverterManager();
      thread = newThread();
      caller = new LuaCaller(converterManager);
    }

    public void testThrow(String filename) throws Exception {
      try {
        lua(filename);
        Object func = lastEnv.rawget("throwFail");
        thread.call(func, null, null, null);

        throw new Exception("must be throw");

      } catch (RuntimeException e) {
        Throwable cause = e.getCause();
        if (cause == null) cause = e;
        if ("ok".equals(cause.getMessage()) != true) {
          Tool.printTable(lastEnv);
          throw e;
        }
        Tool.pl("Throw test pass");
      }
    }

    public void lua(String filename) throws Exception {
      FileInputStream fi = new FileInputStream(filename);
      LuaClosure closure = LuaCompiler.loadis(fi, filename, lastEnv);
      LuaReturn ret = LuaReturn.createReturn(caller.pcall(thread, closure));

      if (ret.size() > 0) {
        for (int i = 0; i < ret.size(); ++i) {
          Tool.pl("  return", i, ret.get(i));
        }
      } else {
        Tool.pl("  return NONE");
      }

      if (ret.isSuccess()) {
        Tool.pl("OK", filename);
      } else {
        Tool.pl("ERROR", filename);
        DebugInf.printLuaStack(thread.currentCoroutine);
        Tool.printTable(lastEnv);
        printError(ret);
      }

      Test.verifyCorrectStack(thread);
    }
  }


  private static void test_signature() throws Exception {
    Field f = TestKahluaThread2.class.getField("callFrame");

    pl("Test signature:");
    pl("getName: "+ f.getName());
    pl("getType.getname: "+ f.getType().getName());
    pl("getDeclaringClass: "+ f.getDeclaringClass().getName());

    SignatureWriter signature = new SignatureWriter();
    signature.visitClassType(f.getType().getName());
    signature.visitEnd();

    pl("signature1: "+ signature.toString());


    Method m = f.getType().getMethod("set", int.class, Object.class);

    SignatureWriter s2 = new SignatureWriter();
    for (Type pt : m.getParameterTypes()) {
        pl("? "+ pt);
      s2.visitParameterType().visitClassType(pt.getTypeName());
    }
    s2.visitReturnType().visitClassType(m.getReturnType().getName());
    s2.visitEnd();

    pl("Method: "+ m.getName());
    pl("signature2: "+ s2.toString());

    SignatureWriter s3 = new SignatureWriter();
    s3.visitParameterType().visitBaseType('I');
    s3.visitReturnType().visitBaseType('V');
    s3.visitEnd();

    pl("S3 "+ s3.toString());
  }


  public static void printError(LuaReturn ret) {
    if (ret.isSuccess()) return;
    System.out.println("Lua Error: "+ ret.getErrorString());
    System.out.println(formatTrace(ret.getLuaStackTrace()));
    Exception e = ret.getJavaException();
    if (e != null) {
      e.printStackTrace();
    }
  }


  public static String formatTrace(String t) {
    StringBuilder b = new StringBuilder(t.length() + 10);
    String[] sp = t.split("\n");
    for (String line : sp) {
      b.append("\t");
      b.append(line);
      b.append("\n");
    }
    return b.toString();
  }


  @Override
  public void run() {
  }


  public static void pl(Object o) {
    Tool.pl(o);
  }

}
