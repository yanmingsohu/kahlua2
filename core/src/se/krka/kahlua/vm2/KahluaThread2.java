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

  private String outputDir;
  final Platform platform;
  public static boolean debug;


  public KahluaThread2(Platform platform, KahluaTable environment) {
    this(System.out, platform, environment);
  }


  public KahluaThread2(PrintStream s, Platform p, KahluaTable e) {
    super(s, p, e);
    this.platform = p;
  }


  public int call(int nArguments) {
    ComputStack cs = new ComputStack(currentCoroutine, nArguments);
    Object o = currentCoroutine.objectStack[cs.returnBase];

    if (o == null) {
      throw new RuntimeException("tried to call nil");
    }
    if (debug) {
      Tool.pl(cs, "Func:", o);
    }

    if (o instanceof JavaFunction) {
      return callJava((JavaFunction) o, cs.localBase, cs.returnBase, nArguments);
    }

    if (o instanceof ClosureInf) {
      ClosureInf ci = (ClosureInf)o;
      LuaCallFrame f = currentCoroutine.currentCallFrame();
      if (f != null) {
        ci.setFrame(f.closure, f);
      } else {
        ci.frameParams(cs);
      }
      ci.call();
      return cs.returnValues(currentCoroutine);
    }

    if (!(o instanceof LuaClosure)) {
      throw new RuntimeException("tried to call a non-function");
    }

    try {
      LuaClosure lc = (LuaClosure) o;
      LuaBuilder luab = new LuaBuilder(lc.prototype.name, outputDir);
      luab.debug = debug;
      luab.makeJavacode(lc.prototype);

      LuaScript x = luab.createJavaAgent();
      x.reinit(this, currentCoroutine);
      x.run(); //TODO: Add thread running strategy

    } catch (NoSuchMethodException | InstantiationException
          | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }

    int nReturnValues = cs.returnValues(currentCoroutine);
    currentCoroutine.stackTrace = "";
    return nReturnValues;
  }


  public int call(LuaClosure oldc, Coroutine cor, int nArguments) {
    currentCoroutine = cor;
    ComputStack cs = new ComputStack(currentCoroutine, nArguments);

    LuaCallFrame callFrame = currentCoroutine.pushNewCallFrame(oldc, null,
      cs.localBase, cs.returnBase, nArguments, false, true);
    callFrame.init();

    if (debug) {
      Tool.pl("Call old thread", cs, oldc);
    }
    luaMainloop();

    int nReturnValues = cs.returnValues(currentCoroutine);
    currentCoroutine.stackTrace = "";
    currentCoroutine.popCallFrame();
    return nReturnValues;
  }


  public static String metaOpName(int i) {
    return meta_ops[i];
  }


  public static int opNamesLen() {
    return DebugInf.opNames.length;
  }


  public static String opName(int i) {
    return DebugInf.opNames[i];
  }


  public void setOutputDir(String dir) {
    this.outputDir = dir;
  }
}
