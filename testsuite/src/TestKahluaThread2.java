/*
 Copyright (c) 2023 Kristofer Karlsson <kristofer.karlsson@gmail.com>

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

import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import se.krka.kahlua.converter.KahluaConverterManager;
import se.krka.kahlua.integration.LuaCaller;
import se.krka.kahlua.integration.LuaReturn;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.*;

import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;


public class TestKahluaThread2 implements Runnable {
  public LuaCallFrame callFrame;


  public static void main(String[] av) throws Exception {
//    testVM();
    testMaker();
//    test1();
    System.out.println("done");
  }


  private static void testMaker() throws Exception {
    KahluaConverterManager converterManager = new KahluaConverterManager();
    Platform plat = J2SEPlatform.getInstance();
    KahluaTable env = plat.newEnvironment();
    KahluaThread2 thread = new KahluaThread2(plat, env);

    thread.test();
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
    System.out.println(o);
  }
}
