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

import org.objectweb.asm.Label;
import org.objectweb.asm.signature.SignatureWriter;
import se.krka.kahlua.converter.KahluaConverterManager;
import se.krka.kahlua.integration.LuaCaller;
import se.krka.kahlua.integration.LuaReturn;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.*;
import se.krka.kahlua.vm2.*;

import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;


public class TestKahluaThread2 implements Runnable {
  public LuaCallFrame callFrame;


  public void ___asm() {
    double x = 100;
    double y = 999;
    if (x < y)
      Tool.pl(null, null, x,y);
    boolean a = true;
    boolean b = !a;
    Tool.pl(a, b);
  }


  public static void main(String[] av) throws Exception {
    testVM();
//    testLuaBuilder();

//    test1();
    Tool.pl("Done");
  }


  private static void testLuaBuilder() throws Exception {
    new TestLuaBuild().test_every_lua_code();
  }


  private static void testVM() throws Exception {
    KahluaConverterManager converterManager = new KahluaConverterManager();
    Platform plat = J2SEPlatform.getInstance();
    KahluaTable env = plat.newEnvironment();
    KahluaThread2 thread = new KahluaThread2(plat, env);
    LuaCaller caller = new LuaCaller(converterManager);

    final String filename = "./testsuite/lua/testhelper.lua";
    FileInputStream fi = new FileInputStream(filename);
    LuaClosure closure = LuaCompiler.loadis(fi, filename, env);
    LuaReturn ret = LuaReturn.createReturn(caller.pcall(thread, closure));

    printError(ret);
  }


  private static void test1() throws Exception {
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


  public static void hello() {
    System.out.println("hello");
  }


  @Override
  public void run() {
  }


  public static void pl(Object o) {
    Tool.pl(o);
  }


  public static class TestLuaBuild extends LuaBuilder {

    public TestLuaBuild() {
      super("./testsuite/lua/testhelper.lua");
    }


    public void test_every_lua_code() throws Exception {
      int endIndex = 37;

      Platform platform = J2SEPlatform.getInstance();
      KahluaTable env = platform.newEnvironment();
      KahluaThread2 kt2 = new KahluaThread2(platform, env);

      final String line = " -------------------------------------------- ";
      Prototype p = new Prototype();
      p.name = "./testsuite/lua/testhelper.lua";
      p.constants = new Object[]{};
      p.lines = new int[KahluaThread2.opNamesLen()];

      LuaClosure lc = new LuaClosure(p, env);
      Coroutine cr = new Coroutine(platform, env, kt2);
      cr.pushNewCallFrame(lc, null, 0,0,0, false, true);

      Label[] labels = new Label[p.lines.length];
      this.labels = labels;

      cm.defaultInit();
      mv = cm.beginMethod("run");
      ClosureInf ci = new ClosureInf(p, 0, "run");
      cm.vClosureFunctionHeader(ci);

      Set<String> skip = new HashSet();
      skip.add("op_jmp");
//      skip.add("op_test");
//      skip.add("op_testset");
      skip.add("op_closure");
      skip.add("op_forprep");

      for (int i=0; i<p.lines.length; ++i) {
        p.lines[i] = i;
        labels[i] = new Label();
      }

      for (int i=0; i<=endIndex; ++i) {
        pc = i+1;
        op = ((1)<<6) | ((3)<<14) | ((2)<<23);
        opcode = i & 0x3F;
        Label label = labels[i];
        this.line = p.lines[i];

        String opName = KahluaThread2.opName(i).toLowerCase();
        if (skip.contains(opName)) {
          mv.visitLabel(label);
          Tool.pl("!> SKIP", opName);
          continue;
//          op = (1 + 0x1ffff) << 14;
        }

        Tool.pl(">> ", opName, "()");
        mv.visitLabel(label);
        mv.visitLineNumber(p.lines[i], label);
        vDebugInf();

        do_op_code(i, ci);
        Tool.pl("> ok");
      }

      cm.vPrint("END / "+ new Date().toString());
      cm.endMethod();

      LuaScript agent = createJavaAgent();
      agent.reinit(kt2, cr);
      agent.run();
    }
  }
}
