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

package se.krka.kahlua.vm2;

import se.krka.kahlua.vm.*;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;


/**
 * KahluaThread is modified into an inheritable class,
 * And the `luaMainloop` method is changed to protected method without final.
 */
public class KahluaThread2 extends KahluaThread {

  private static final DebugInf.Statistics st = new DebugInf.Statistics();

  private String outputDir;
  private final DebugInf di;
  final Platform platform;


  public KahluaThread2(Platform platform, KahluaTable environment) {
    this(System.out, platform, environment);
  }


  public KahluaThread2(PrintStream s, Platform p, KahluaTable e) {
    super(s, p, e);
    this.platform = p;
    this.di = new DebugInf(DebugInf.NONE, this.st);
  }


  @Override
  public int call(int nArguments) {
    ComputStack cs = new ComputStack(currentCoroutine, nArguments);
    Object o = currentCoroutine.objectStack[cs.returnBase];

    if (o == null) {
      throw new LuaFail("tried to call nil");
    }
    if (di.has(DebugInf.CALL)) {
      Tool.pl(cs, "Func:", o.getClass(), o);
    }

    if (o instanceof JavaFunction) {
      return callJava((JavaFunction) o, cs.localBase, cs.returnBase, nArguments);
    }

    if (o instanceof ClosureInf) {
      ClosureInf ci = (ClosureInf)o;
      ci.frameParams(cs);
      ci.call();
      return cs.returnValues(currentCoroutine);
    }

    if (!(o instanceof LuaClosure)) {
      throw new LuaFail("tried to call a non-function");
    }

    try {
      LuaClosure lc = (LuaClosure) o;
      LuaBuilder luab = new LuaBuilder(di, lc.prototype.name, outputDir);
      luab.makeJavacode(lc.prototype);

      LuaScript x = luab.createJavaAgent();
      x.reinit(this, currentCoroutine, cs, di.flag);
      x.run(); //TODO: Add thread running/compile strategy

    } catch (NoSuchMethodException | InstantiationException
          | IllegalAccessException | InvocationTargetException e) {
      throw new LuaFail(e);
    }

    int nReturnValues = cs.returnValues(currentCoroutine);
    //currentCoroutine.stackTrace = "";
    return nReturnValues;
  }


  public static void printStatistics() {
    Tool.pl(st);
  }


  public int call(LuaClosure oldc, Coroutine cor, int nArguments) {
    currentCoroutine = cor;
    ComputStack cs = new ComputStack(currentCoroutine, nArguments);

    LuaCallFrame callFrame = currentCoroutine.pushNewCallFrame(
        oldc, null, cs.localBase, cs.returnBase, nArguments, false, false);
    callFrame.init();

    if (di.has(DebugInf.CALL)) {
      Tool.pl("Call old thread", cs, oldc, "FrameTop:", currentCoroutine.getCallframeTop());
    }
    if (di.has(DebugInf.STACK)) {
      DebugInf.printLuaStack(cor, callFrame);
    }

    // This was call popCallFrame() when back
    luaMainloop();


    int nReturnValues = cs.returnValues(currentCoroutine);
    //currentCoroutine.stackTrace = "";
    return nReturnValues;
  }


  public static String metaOpName(int i) {
    return meta_ops[i];
  }


  public void setOutputDir(String dir) {
    this.outputDir = dir;
  }


  public void setDebug(int ...flag) {
    for (int f : flag) {
      this.di.flag |= f;
    }
  }


  public void setDebug(DebugInf di) {
    this.di.flag = di.flag;
  }


  public void printStack() {
    DebugInf.printLuaStack(currentCoroutine);
  }
}
